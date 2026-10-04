package com.curiouskids.club.identity.internal.web;

import static com.curiouskids.club.support.Csrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.curiouskids.club.notification.internal.outbox.OutboxTestSupport;
import com.curiouskids.club.support.IntegrationTest;
import com.curiouskids.club.support.Mailpit;
import com.curiouskids.club.support.TestAccounts;
import com.curiouskids.club.support.TestAccounts.Account;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class ProfileTest extends IntegrationTest {

  private static final String NEW_PASSWORD = "orange penguins dance quietly";
  private static final Pattern TOKEN = Pattern.compile("confirm-email\\?token=([A-Za-z0-9_-]+)");

  @Autowired TestAccounts accounts;
  @Autowired JdbcClient jdbc;
  @Autowired JsonMapper json;

  private final Mailpit mailpit = mailpit();

  // Profile and reminder preferences

  @Test
  @DisplayName("FR-ID-06: a member sees their details, with both reminder toggles on by default")
  void profileDefaults() throws Exception {
    Account member = accounts.member(true);

    mvc.perform(get("/api/v1/me/profile").with(member.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Test Family"))
        .andExpect(jsonPath("$.email").value(member.email()))
        .andExpect(jsonPath("$.remindPickup").value(true))
        .andExpect(jsonPath("$.remindDueSoon").value(true))
        .andExpect(jsonPath("$.phone").doesNotExist())
        .andExpect(jsonPath("$.pendingEmail").doesNotExist());
  }

  @Test
  @DisplayName("FR-ID-06: a member changes name, phone and reminder toggles; absent fields stay")
  void updateProfile() throws Exception {
    Account member = accounts.member(true);
    Account other = accounts.member(true);

    send(
            member,
            patch("/api/v1/me/profile"),
            Map.of("name", " Sam Parent ", "phone", "+44 7700 900123", "remindPickup", false))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Sam Parent"))
        .andExpect(jsonPath("$.phone").value("+44 7700 900123"))
        .andExpect(jsonPath("$.remindPickup").value(false))
        .andExpect(jsonPath("$.remindDueSoon").value(true));

    send(member, patch("/api/v1/me/profile"), Map.of("phone", "", "remindDueSoon", false))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Sam Parent"))
        .andExpect(jsonPath("$.phone").doesNotExist())
        .andExpect(jsonPath("$.remindPickup").value(false))
        .andExpect(jsonPath("$.remindDueSoon").value(false));

    Map<String, Object> stored =
        jdbc.sql(
                """
                select f.display_name, f.phone, a.remind_pickup, a.remind_due_soon
                from account a join family f on f.id = a.family_id where a.id = :id
                """)
            .param("id", member.id())
            .query()
            .singleRow();
    assertThat(stored)
        .containsEntry("display_name", "Sam Parent")
        .containsEntry("phone", null)
        .containsEntry("remind_pickup", false)
        .containsEntry("remind_due_soon", false);
    mvc.perform(get("/api/v1/me/profile").with(other.session()))
        .andExpect(jsonPath("$.name").value("Test Family"))
        .andExpect(jsonPath("$.remindPickup").value(true));
  }

  @Test
  @DisplayName("FR-ID-06: overdue reminders cannot be switched off; other fields are refused")
  void unknownProfileFieldsRefused() throws Exception {
    Account member = accounts.member(true);

    for (String field : List.of("remindOverdue", "email", "familyId")) {
      send(member, patch("/api/v1/me/profile"), Map.of(field, false))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.errors[0].field").value(field));
    }
  }

  @Test
  @DisplayName("FR-ID-06: a blank or over-long name and a malformed phone get field errors")
  void invalidProfileRefused() throws Exception {
    Account member = accounts.member(true);

    send(member, patch("/api/v1/me/profile"), Map.of("name", "   "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("name"));
    send(member, patch("/api/v1/me/profile"), Map.of("name", "x".repeat(101)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("name"));
    send(member, patch("/api/v1/me/profile"), Map.of("phone", "call me"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("phone"));
  }

  @Test
  @DisplayName("FR-ID-06: the profile is for members; staff get 403 and anonymous callers 401")
  void profileIsForMembers() throws Exception {
    mvc.perform(get("/api/v1/me/profile").with(accounts.volunteer().session()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/me/profile")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/me/password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
  }

  // Password change

  @Test
  @DisplayName("FR-ID-06: a password change keeps this session under a new ID and ends the others")
  void changePasswordEndsOtherSessions() throws Exception {
    Account member = accounts.member(true);
    Cookie phone = login(member.email(), TestAccounts.PASSWORD);
    Cookie laptop = login(member.email(), TestAccounts.PASSWORD);
    Account other = accounts.member(true);
    Cookie otherSession = login(other.email(), TestAccounts.PASSWORD);

    MockHttpServletResponse response =
        withCookie(
                phone,
                post("/api/v1/me/password"),
                Map.of("currentPassword", TestAccounts.PASSWORD, "newPassword", NEW_PASSWORD))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();

    Cookie renewed = response.getCookie("__Host-SESSION");
    assertThat(renewed).isNotNull();
    assertThat(renewed.getValue()).isNotEqualTo(phone.getValue());
    mvc.perform(get("/api/v1/auth/me").cookie(renewed)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/auth/me").cookie(phone)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me").cookie(laptop)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me").cookie(otherSession)).andExpect(status().isOk());
    loginRequest(member.email(), TestAccounts.PASSWORD).andExpect(status().isUnauthorized());
    loginRequest(member.email(), NEW_PASSWORD).andExpect(status().isOk());
  }

  @Test
  @DisplayName("FR-ID-06: a wrong current password or a weak new one is refused with a field error")
  void changePasswordValidated() throws Exception {
    Account member = accounts.member(true);

    changePassword(member, "not my password", NEW_PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));
    changePassword(member, TestAccounts.PASSWORD, "password1234")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("newPassword"));

    loginRequest(member.email(), TestAccounts.PASSWORD).andExpect(status().isOk());
  }

  @Test
  @DisplayName("FR-ID-03, FR-ID-06: wrong current passwords count as failed logins, then 429")
  void wrongCurrentPasswordsAreLimited() throws Exception {
    Account member = accounts.member(true);
    for (int i = 0; i < 5; i++) {
      changePassword(member, "wrong " + i, NEW_PASSWORD).andExpect(status().isBadRequest());
    }

    changePassword(member, TestAccounts.PASSWORD, NEW_PASSWORD)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
        .andExpect(header().exists("Retry-After"));
    requestEmailChange(member, "new-" + member.id() + "@example.com", TestAccounts.PASSWORD)
        .andExpect(status().isTooManyRequests());
    loginRequest(member.email(), TestAccounts.PASSWORD).andExpect(status().isTooManyRequests());
  }

  @Test
  @DisplayName("FR-ID-06: a reset link sent before a password change no longer works after it")
  void passwordChangeVoidsResetLinks() throws Exception {
    Account member = accounts.member(true);
    unauthenticated("/api/v1/auth/password-reset/request", Map.of("email", member.email()))
        .andExpect(status().isAccepted());

    changePassword(member, TestAccounts.PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

    assertThat(
            jdbc.sql(
                    "select count(*) from verification_token"
                        + " where account_id = :id and type = 'PASSWORD_RESET'")
                .param("id", member.id())
                .query(Long.class)
                .single())
        .isZero();
  }

  // Email change

  @Test
  @DisplayName("FR-ID-06: a new email takes effect only when the link sent to it is used")
  void changeEmail() throws Exception {
    Account member = accounts.member(true);
    String newEmail = "New-" + member.id() + "@Example.com";
    String normalised = newEmail.toLowerCase();

    requestEmailChange(member, newEmail, TestAccounts.PASSWORD).andExpect(status().isAccepted());

    mvc.perform(get("/api/v1/me/profile").with(member.session()))
        .andExpect(jsonPath("$.email").value(member.email()))
        .andExpect(jsonPath("$.pendingEmail").value(normalised));
    loginRequest(member.email(), TestAccounts.PASSWORD).andExpect(status().isOk());
    OutboxTestSupport.dispatch(context);
    Mailpit.Message email = mailpit.awaitMessagesTo(normalised, 1).getFirst();
    assertThat(email.subject()).isEqualTo("Confirm your new email for Curiouskids Club");
    Matcher matcher = TOKEN.matcher(email.text());
    assertThat(matcher.find()).isTrue();

    confirm(matcher.group(1)).andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/me/profile").with(member.session()))
        .andExpect(jsonPath("$.email").value(normalised))
        .andExpect(jsonPath("$.pendingEmail").doesNotExist());
    loginRequest(member.email(), TestAccounts.PASSWORD).andExpect(status().isUnauthorized());
    loginRequest(newEmail, TestAccounts.PASSWORD)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.emailVerified").value(true));
    OutboxTestSupport.dispatch(context);
    Mailpit.Message notice = mailpit.awaitMessagesTo(member.email(), 1).getFirst();
    assertThat(notice.subject()).isEqualTo("Your Curiouskids Club email address was changed");
    assertThat(notice.text()).doesNotContain(normalised);
  }

  @Test
  @DisplayName("FR-ID-06: confirming a new email also verifies an unverified account")
  void confirmVerifiesAccount() throws Exception {
    Account member = accounts.member(false);
    String newEmail = "new-" + member.id() + "@example.com";

    confirm(emailChangeToken(member, newEmail)).andExpect(status().isNoContent());

    loginRequest(newEmail, TestAccounts.PASSWORD)
        .andExpect(jsonPath("$.emailVerified").value(true));
  }

  @Test
  @DisplayName("FR-ID-06: an address that already has an account gets the same 202 and no link")
  void registeredAddressLooksTheSame() throws Exception {
    Account member = accounts.member(true);
    Account owner = accounts.member(true);
    String free = "free-" + member.id() + "@example.com";

    String toFree =
        requestEmailChange(member, free, TestAccounts.PASSWORD)
            .andReturn()
            .getResponse()
            .getContentAsString();
    String toTaken =
        requestEmailChange(member, owner.email(), TestAccounts.PASSWORD)
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(toTaken).isEqualTo(toFree);
    assertThat(outboxCount(owner.email())).isZero();
    // The newer request replaced the link to the free address too.
    assertThat(emailChangeTokens(member)).isZero();
  }

  @Test
  @DisplayName("FR-ID-06: the current address or a wrong password is refused with a field error")
  void emailChangeValidated() throws Exception {
    Account member = accounts.member(true);

    requestEmailChange(member, member.email().toUpperCase(), TestAccounts.PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("newEmail"));
    requestEmailChange(member, "new-" + member.id() + "@example.com", "not my password")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));
    requestEmailChange(member, "not an email", TestAccounts.PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("newEmail"));

    assertThat(emailChangeTokens(member)).isZero();
  }

  @Test
  @DisplayName("FR-ID-06: a link works once, a newer request replaces it, and it expires in 24h")
  void linkIsSingleUseReplacedAndExpires() throws Exception {
    Account member = accounts.member(true);
    String first = emailChangeToken(member, "first-" + member.id() + "@example.com");
    String second = emailChangeToken(member, "second-" + member.id() + "@example.com");

    expectTokenInvalid(confirm(first));
    confirm(second).andExpect(status().isNoContent());
    expectTokenInvalid(confirm(second));

    String third = emailChangeToken(member, "third-" + member.id() + "@example.com");
    assertThat(
            jdbc.sql(
                    "select expires_at - created_at = interval '24 hours' from verification_token"
                        + " where account_id = :id and type = 'EMAIL_CHANGE' and used_at is null")
                .param("id", member.id())
                .query(Boolean.class)
                .single())
        .isTrue();
    jdbc.sql("update verification_token set expires_at = now() - interval '1 second'").update();
    expectTokenInvalid(confirm(third));
  }

  @Test
  @DisplayName("FR-ID-06: if the address got an account after the link was sent, nothing changes")
  void addressTakenBeforeConfirm() throws Exception {
    Account member = accounts.member(true);
    String newEmail = "contested-" + member.id() + "@example.com";
    String token = emailChangeToken(member, newEmail);
    jdbc.sql("update account set email = :email where id = :id")
        .param("email", newEmail)
        .param("id", accounts.member(true).id())
        .update();

    expectTokenInvalid(confirm(token));

    loginRequest(member.email(), TestAccounts.PASSWORD).andExpect(status().isOk());
  }

  @Test
  @DisplayName("FR-ID-06: two accounts confirming the same new address at once: only one gets it")
  void concurrentConfirmsForOneAddress() throws Exception {
    String newEmail = "race-" + System.nanoTime() + "@example.com";
    List<String> tokens = new ArrayList<>();
    // Three: the most change links one address may receive in an hour.
    for (int i = 0; i < 3; i++) {
      tokens.add(emailChangeToken(accounts.member(true), newEmail));
    }

    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(tokens.size())) {
      for (String token : tokens) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return confirm(token).andReturn().getResponse().getStatus();
                }));
      }
      start.countDown();
      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> result : results) {
        statuses.add(result.get());
      }
      assertThat(statuses).containsOnly(204, 400).containsOnlyOnce(204);
    }
    assertThat(
            jdbc.sql("select count(*) from account where email = :email")
                .param("email", newEmail)
                .query(Long.class)
                .single())
        .isOne();
  }

  @Test
  @DisplayName("FR-ID-06: 3 change links per new address per hour, then 429")
  void emailChangeLinksLimited() throws Exception {
    Account member = accounts.member(true);
    String newEmail = "limited-" + member.id() + "@example.com";
    for (int i = 0; i < 3; i++) {
      requestEmailChange(member, newEmail, TestAccounts.PASSWORD).andExpect(status().isAccepted());
    }
    requestEmailChange(member, newEmail, TestAccounts.PASSWORD)
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
  }

  @Test
  @DisplayName("NFR-06: profile logs contain no email, name, phone, password or token")
  void noPersonalDataLogged(CapturedOutput output) throws Exception {
    Account member = accounts.member(true);
    String newEmail = "private-" + member.id() + "@example.com";
    send(member, patch("/api/v1/me/profile"), Map.of("name", "Private Name", "phone", "0123 456"))
        .andExpect(status().isOk());
    changePassword(member, "wrong password", NEW_PASSWORD).andExpect(status().isBadRequest());
    changePassword(member, TestAccounts.PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
    String token = emailChangeToken(member, newEmail, NEW_PASSWORD);
    confirm(token).andExpect(status().isNoContent());

    assertThat(output)
        .contains("Profile changed")
        .contains("Password changed")
        .contains("Email changed")
        .doesNotContain(member.email())
        .doesNotContain(newEmail)
        .doesNotContain("Private Name")
        .doesNotContain("0123 456")
        .doesNotContain(NEW_PASSWORD)
        .doesNotContain(token);
  }

  private String emailChangeToken(Account account, String newEmail) throws Exception {
    return emailChangeToken(account, newEmail, TestAccounts.PASSWORD);
  }

  /** Requests an email change and returns the token from the queued email. */
  private String emailChangeToken(Account account, String newEmail, String password)
      throws Exception {
    requestEmailChange(account, newEmail, password).andExpect(status().isAccepted());
    String path =
        jdbc.sql(
                """
                select payload ->> 'path' from notification_outbox
                where recipient_account_id = :id and type = 'EMAIL_CHANGE'
                order by created_at desc limit 1
                """)
            .param("id", account.id())
            .query(String.class)
            .single();
    Matcher matcher = TOKEN.matcher(path);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private long emailChangeTokens(Account account) {
    return jdbc.sql(
            "select count(*) from verification_token"
                + " where account_id = :id and type = 'EMAIL_CHANGE' and used_at is null")
        .param("id", account.id())
        .query(Long.class)
        .single();
  }

  private long outboxCount(String email) {
    return jdbc.sql("select count(*) from notification_outbox where recipient_email = :e")
        .param("e", email)
        .query(Long.class)
        .single();
  }

  private ResultActions changePassword(Account account, String current, String next)
      throws Exception {
    return send(
        account,
        post("/api/v1/me/password"),
        Map.of("currentPassword", current, "newPassword", next));
  }

  private ResultActions requestEmailChange(Account account, String newEmail, String password)
      throws Exception {
    return send(
        account,
        post("/api/v1/me/email"),
        Map.of("newEmail", newEmail, "currentPassword", password));
  }

  private ResultActions confirm(String token) throws Exception {
    return unauthenticated("/api/v1/auth/email-change/confirm", Map.of("token", token));
  }

  private ResultActions loginRequest(String email, String password) throws Exception {
    return unauthenticated("/api/v1/auth/login", Map.of("email", email, "password", password));
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

  private ResultActions send(Account account, MockHttpServletRequestBuilder request, Object body)
      throws Exception {
    return mvc.perform(
        request
            .with(account.session())
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  private ResultActions withCookie(
      Cookie session, MockHttpServletRequestBuilder request, Object body) throws Exception {
    return mvc.perform(
        request
            .cookie(session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  private ResultActions unauthenticated(String path, Object body) throws Exception {
    return mvc.perform(
        post(path)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  private static void expectTokenInvalid(ResultActions result) throws Exception {
    result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
  }
}
