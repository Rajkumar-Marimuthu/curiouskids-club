package com.curiouskids.club.identity.internal.web;

import static com.curiouskids.club.support.Csrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.curiouskids.club.notification.internal.outbox.OutboxTestSupport;
import com.curiouskids.club.support.IntegrationTest;
import com.curiouskids.club.support.Mailpit;
import com.curiouskids.club.support.TestAccounts;
import com.curiouskids.club.support.TestAccounts.Account;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class PasswordResetTest extends IntegrationTest {

  private static final String NEW_PASSWORD = "orange penguins dance quietly";
  private static final Pattern TOKEN = Pattern.compile("reset-password\\?token=([A-Za-z0-9_-]+)");

  @Autowired TestAccounts accounts;
  @Autowired JdbcClient jdbc;
  @Autowired JsonMapper json;

  private final Mailpit mailpit = mailpit();

  @Test
  @DisplayName("FR-ID-04: a registered email gets a one-hour, single-use reset link by email")
  void requestQueuesResetEmail() throws Exception {
    Account member = accounts.member(true);

    requestReset(member.email()).andExpect(status().isAccepted());

    Map<String, Object> token =
        jdbc.sql(
                """
                select type, expires_at - created_at = interval '1 hour' as one_hour
                from verification_token where account_id = :id
                """)
            .param("id", member.id())
            .query()
            .singleRow();
    assertThat(token).containsEntry("type", "PASSWORD_RESET").containsEntry("one_hour", true);
    OutboxTestSupport.dispatch(context);
    Mailpit.Message email = mailpit.awaitMessagesTo(member.email(), 1).getFirst();
    assertThat(email.subject()).isEqualTo("Reset your Curiouskids Club password");
    assertThat(email.text()).containsPattern(TOKEN);
  }

  @Test
  @DisplayName("FR-ID-04: an unknown email gets the same response and no email")
  void unknownEmailLooksTheSame() throws Exception {
    Account member = accounts.member(true);
    String unknown = "nobody-" + member.id() + "@example.com";

    String known = requestReset(member.email()).andReturn().getResponse().getContentAsString();
    String other =
        requestReset(unknown)
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(other).isEqualTo(known);
    assertThat(
            jdbc.sql("select count(*) from notification_outbox where recipient_email = :e")
                .param("e", unknown)
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  @DisplayName("FR-ID-04: a disabled account gets no reset email")
  void disabledAccountGetsNothing() throws Exception {
    Account member = accounts.member(true);
    accounts.disable(member);

    requestReset(member.email()).andExpect(status().isAccepted());

    assertThat(tokenCount(member)).isZero();
  }

  @Test
  @DisplayName("FR-ID-04: a valid link and strong password change it and end every session")
  void confirmChangesPasswordAndEndsSessions() throws Exception {
    Account member = accounts.member(true);
    Cookie phone = login(member.email(), TestAccounts.PASSWORD);
    Cookie laptop = login(member.email(), TestAccounts.PASSWORD);
    Account other = accounts.member(true);
    Cookie otherSession = login(other.email(), TestAccounts.PASSWORD);

    confirm(resetToken(member), NEW_PASSWORD).andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/auth/me").cookie(phone)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me").cookie(laptop)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me").cookie(otherSession)).andExpect(status().isOk());
    loginRequest(member.email(), TestAccounts.PASSWORD).andExpect(status().isUnauthorized());
    loginRequest(member.email(), NEW_PASSWORD).andExpect(status().isOk());
  }

  @Test
  @DisplayName("FR-ID-04: a completed reset marks the email verified and clears failed logins")
  void confirmVerifiesEmailAndClearsLockout() throws Exception {
    Account member = accounts.member(false);
    for (int i = 0; i < 5; i++) {
      loginRequest(member.email(), "wrong " + i).andExpect(status().isUnauthorized());
    }
    loginRequest(member.email(), "wrong").andExpect(status().isTooManyRequests());

    confirm(resetToken(member), NEW_PASSWORD).andExpect(status().isNoContent());

    loginRequest(member.email(), NEW_PASSWORD)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.emailVerified").value(true));
  }

  @Test
  @DisplayName("FR-ID-04: a link works once, and a newer link replaces an older one")
  void linkIsSingleUseAndReplaced() throws Exception {
    Account member = accounts.member(true);
    String first = resetToken(member);
    String second = resetToken(member);

    expectTokenInvalid(confirm(first, NEW_PASSWORD));
    confirm(second, NEW_PASSWORD).andExpect(status().isNoContent());
    expectTokenInvalid(confirm(second, "yet another long password"));
  }

  @Test
  @DisplayName("FR-ID-04: a link older than an hour no longer works")
  void expiredLinkRefused() throws Exception {
    Account member = accounts.member(true);
    String token = resetToken(member);
    jdbc.sql("update verification_token set expires_at = now() - interval '1 second'").update();

    expectTokenInvalid(confirm(token, NEW_PASSWORD));
  }

  @Test
  @DisplayName("FR-ID-04: a weak new password is refused with a field error and keeps the link")
  void weakPasswordRefused() throws Exception {
    Account member = accounts.member(true);
    String token = resetToken(member);

    confirm(token, "password1234")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("password"));

    confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("FR-ID-04: 3 emailed links per email per hour, known or unknown; then 429")
  void emailLinksLimitedPerEmail() throws Exception {
    Account member = accounts.member(false);
    String unknown = "nobody-" + member.id() + "@example.com";
    for (String email : List.of(member.email(), unknown)) {
      requestReset(email).andExpect(status().isAccepted());
      resend(email).andExpect(status().isAccepted());
      requestReset(email).andExpect(status().isAccepted());
      requestReset(email)
          .andExpect(status().isTooManyRequests())
          .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
          .andExpect(header().exists("Retry-After"));
      resend(email).andExpect(status().isTooManyRequests());
    }
    // Blocked requests issue nothing: the member has one live reset link and one verification link.
    assertThat(tokenCount(member)).isEqualTo(2);

    jdbc.sql("update request_throttle set at = at - interval '1 hour'").update();
    requestReset(member.email()).andExpect(status().isAccepted());
  }

  @Test
  @DisplayName("FR-ID-04: 20 emailed links per address per hour, then 429")
  void emailLinksLimitedPerAddress() throws Exception {
    for (int i = 0; i < 20; i++) {
      requestReset("someone-" + i + "@example.com").andExpect(status().isAccepted());
    }
    requestReset("one-more@example.com").andExpect(status().isTooManyRequests());
  }

  @Test
  @DisplayName("FR-ID-01: 10 registrations per address per hour, then 429")
  void registrationsLimitedPerAddress() throws Exception {
    for (int i = 0; i < 10; i++) {
      register("family-" + i + "-" + System.nanoTime() + "@example.com")
          .andExpect(status().isAccepted());
    }
    register("family-x-" + System.nanoTime() + "@example.com")
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
  }

  @Test
  @DisplayName("NFR-06: request limits store only hashes; reset logs contain no email or token")
  void noPersonalDataStoredOrLogged(CapturedOutput output) throws Exception {
    Account member = accounts.member(true);
    String token = resetToken(member);
    confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());

    assertThat(jdbc.sql("select key_hash from request_throttle").query(String.class).list())
        .isNotEmpty()
        .allSatisfy(key -> assertThat(key).matches("(email|ip):[0-9a-f]{64}"));
    assertThat(output)
        .contains("Password reset requested")
        .contains("Password reset")
        .doesNotContain(member.email())
        .doesNotContain(token)
        .doesNotContain(NEW_PASSWORD);
  }

  /** Requests a reset and returns the token from the queued email. */
  private String resetToken(Account account) throws Exception {
    requestReset(account.email()).andExpect(status().isAccepted());
    String path =
        jdbc.sql(
                """
                select payload ->> 'path' from notification_outbox
                where recipient_account_id = :id and type = 'PASSWORD_RESET'
                order by created_at desc limit 1
                """)
            .param("id", account.id())
            .query(String.class)
            .single();
    Matcher matcher = TOKEN.matcher(path);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private long tokenCount(Account account) {
    return jdbc.sql("select count(*) from verification_token where account_id = :id")
        .param("id", account.id())
        .query(Long.class)
        .single();
  }

  private ResultActions requestReset(String email) throws Exception {
    return post("/api/v1/auth/password-reset/request", Map.of("email", email));
  }

  private ResultActions resend(String email) throws Exception {
    return post("/api/v1/auth/verify-email/resend", Map.of("email", email));
  }

  private ResultActions confirm(String token, String password) throws Exception {
    return post(
        "/api/v1/auth/password-reset/confirm", Map.of("token", token, "password", password));
  }

  private ResultActions register(String email) throws Exception {
    return post(
        "/api/v1/auth/register",
        Map.of(
            "email",
            email,
            "password",
            NEW_PASSWORD,
            "name",
            "Sam Parent",
            "confirmAdult",
            true,
            "acceptTerms",
            true));
  }

  private ResultActions loginRequest(String email, String password) throws Exception {
    return post("/api/v1/auth/login", Map.of("email", email, "password", password));
  }

  private Cookie login(String email, String password) throws Exception {
    Cookie cookie =
        loginRequest(email, password)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie("__Host-SESSION");
    assertThat(cookie).isNotNull();
    return cookie;
  }

  private static void expectTokenInvalid(ResultActions result) throws Exception {
    result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
  }

  private ResultActions post(String path, Map<String, ?> body) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.post(path)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }
}
