package com.curiouskids.club.identity.internal.web;

import static com.curiouskids.club.support.Csrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.curiouskids.club.identity.internal.RegistrationService;
import com.curiouskids.club.notification.internal.outbox.OutboxTestSupport;
import com.curiouskids.club.support.IntegrationTest;
import com.curiouskids.club.support.Mailpit;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class RegistrationTest extends IntegrationTest {

  private static final String STRONG_PASSWORD = "purple giraffes read slowly";
  private static final Pattern TOKEN = Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

  @Autowired JdbcClient jdbc;
  @Autowired JsonMapper json;
  @Autowired RegistrationService registration;

  private final Mailpit mailpit = mailpit();

  @BeforeEach
  void emptyTables() {
    jdbc.sql("delete from notification_outbox").update();
    jdbc.sql("delete from verification_token").update();
    jdbc.sql("delete from account").update();
    jdbc.sql("delete from family").update();
  }

  @Test
  @DisplayName("FR-ID-01: a new parent registers an unverified MEMBER and a verification email")
  void registersUnverifiedMember() throws Exception {
    String email = uniqueEmail();

    register(email, STRONG_PASSWORD).andExpect(status().isAccepted());

    Map<String, Object> account =
        jdbc.sql(
                """
                select a.email, a.role, a.status, a.email_verified_at, a.password_hash,
                       a.consent_version, a.consent_at, f.display_name, f.phone
                from account a join family f on f.id = a.family_id
                """)
            .query()
            .singleRow();
    assertThat(account)
        .containsEntry("email", email)
        .containsEntry("role", "MEMBER")
        .containsEntry("status", "ACTIVE")
        .containsEntry("email_verified_at", null)
        .containsEntry("consent_version", "2026-10")
        .containsEntry("display_name", "Sam Parent")
        .containsEntry("phone", "+44 7700 900123");
    assertThat(account.get("consent_at")).isNotNull();
    assertThat((String) account.get("password_hash"))
        .startsWith("{argon2}$argon2id$")
        .doesNotContain(STRONG_PASSWORD);
    assertThat(outboxCountFor(email)).isEqualTo(1);
  }

  @Test
  @DisplayName("FR-ID-01: the email is stored lower-cased, so another case is the same account")
  void emailIsCaseInsensitive() throws Exception {
    String email = uniqueEmail();

    register(email.toUpperCase(), STRONG_PASSWORD).andExpect(status().isAccepted());
    register(email, STRONG_PASSWORD).andExpect(status().isAccepted());

    assertThat(jdbc.sql("select email from account").query(String.class).list())
        .containsExactly(email);
  }

  @Test
  @DisplayName("FR-ID-01: registering a known email gives the same response and sends nothing")
  void existingEmailIsNotRevealed() throws Exception {
    String known = uniqueEmail();
    register(known, STRONG_PASSWORD).andExpect(status().isAccepted());

    MvcResult forNew = register(uniqueEmail(), STRONG_PASSWORD).andReturn();
    MvcResult forKnown = register(known, "another long passphrase").andReturn();

    assertThat(forKnown.getResponse().getStatus()).isEqualTo(forNew.getResponse().getStatus());
    assertThat(forKnown.getResponse().getContentAsString())
        .isEqualTo(forNew.getResponse().getContentAsString());
    assertThat(headersWithoutTraceId(forKnown)).isEqualTo(headersWithoutTraceId(forNew));
    assertThat(outboxCountFor(known)).isEqualTo(1);
  }

  @Test
  @DisplayName("FR-ID-01: weak passwords are rejected with a field error")
  void weakPasswordsRejected() throws Exception {
    register(uniqueEmail(), "short")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("password"));
    register(uniqueEmail(), "PasswordPassword")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("password"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value(org.hamcrest.Matchers.containsString("too common")));

    assertThat(jdbc.sql("select count(*) from account").query(Long.class).single()).isZero();
  }

  @Test
  @DisplayName("FR-ID-01: both consent boxes must be ticked (BR-28)")
  void consentRequired() throws Exception {
    Map<String, Object> body = new java.util.HashMap<>(body(uniqueEmail(), STRONG_PASSWORD));
    body.put("confirmAdult", false);
    body.remove("acceptTerms");

    MvcResult result =
        mvc.perform(
                post("/api/v1/auth/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .contains("\"confirmAdult\"")
        .contains("\"acceptTerms\"");
  }

  @Test
  @DisplayName("FR-ID-01: two registrations of one email at the same moment create one account")
  void concurrentRegistrationsCreateOneAccount() throws Exception {
    String email = uniqueEmail();
    int threads = 6;
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      for (int i = 0; i < threads; i++) {
        String address = "192.0.2." + i;
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  registration.register(
                      new RegistrationService.Registration(
                          email, STRONG_PASSWORD, "Sam Parent", null),
                      address);
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> result : results) {
        result.get(); // none of them fails
      }
    }

    assertThat(jdbc.sql("select count(*) from account").query(Long.class).single()).isEqualTo(1);
    assertThat(jdbc.sql("select count(*) from family").query(Long.class).single()).isEqualTo(1);
    assertThat(outboxCountFor(email)).isEqualTo(1);
  }

  @Test
  @DisplayName("FR-ID-02: the emailed link verifies the account once")
  void emailedLinkVerifiesOnce() throws Exception {
    String email = uniqueEmail();
    register(email, STRONG_PASSWORD).andExpect(status().isAccepted());
    OutboxTestSupport.dispatch(context);

    Mailpit.Message message = mailpit.awaitMessagesTo(email, 1).getFirst();
    assertThat(message.subject()).isEqualTo("Please confirm your email for Curiouskids Club");
    String token = tokenIn(message.text());

    verify(token).andExpect(status().isNoContent());
    assertThat(verifiedAt(email)).isNotNull();

    verify(token)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
  }

  @Test
  @DisplayName("FR-ID-02: an expired link is refused and the account stays unverified")
  void expiredLinkRefused() throws Exception {
    String email = uniqueEmail();
    register(email, STRONG_PASSWORD);
    String token = queuedToken(email);
    jdbc.sql("update verification_token set expires_at = now() - interval '1 second'").update();

    verify(token)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    assertThat(verifiedAt(email)).isNull();
  }

  @Test
  @DisplayName("FR-ID-02: a link expires 24 hours after it is issued")
  void linkLivesTwentyFourHours() throws Exception {
    register(uniqueEmail(), STRONG_PASSWORD);

    assertThat(
            jdbc.sql("select expires_at - created_at = interval '24 hours' from verification_token")
                .query(Boolean.class)
                .single())
        .isTrue();
  }

  @Test
  @DisplayName("FR-ID-02: an unknown token is refused")
  void unknownTokenRefused() throws Exception {
    verify("not-a-real-token")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
  }

  @Test
  @DisplayName("FR-ID-02: asking for a new link sends one and makes the old link invalid")
  void resendReplacesOldLink() throws Exception {
    String email = uniqueEmail();
    register(email, STRONG_PASSWORD);
    String oldToken = queuedToken(email);

    resend(email.toUpperCase()).andExpect(status().isAccepted());

    assertThat(outboxCountFor(email)).isEqualTo(2);
    String newToken = queuedToken(email);
    assertThat(newToken).isNotEqualTo(oldToken);
    verify(oldToken).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    verify(newToken).andExpect(status().isNoContent());
  }

  @Test
  @DisplayName(
      "FR-ID-02: a new link is not sent for unknown or verified emails, and nobody can tell")
  void resendIsSilentForUnknownAndVerified() throws Exception {
    String verified = uniqueEmail();
    register(verified, STRONG_PASSWORD);
    verify(queuedToken(verified)).andExpect(status().isNoContent());

    resend(uniqueEmail()).andExpect(status().isAccepted());
    resend(verified).andExpect(status().isAccepted());

    assertThat(jdbc.sql("select count(*) from notification_outbox").query(Long.class).single())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("NFR-06: logs of registering and verifying contain no email, name or token")
  void logsHoldNoPersonalData(CapturedOutput output) throws Exception {
    String email = uniqueEmail();
    register(email, STRONG_PASSWORD);
    register(email, STRONG_PASSWORD);
    String token = queuedToken(email);
    verify(token);
    resend(email);

    assertThat(output)
        .contains("Family registered")
        .contains("Email verified")
        .doesNotContain(email)
        .doesNotContain("Sam Parent")
        .doesNotContain(token)
        .doesNotContain(STRONG_PASSWORD);
  }

  private ResultActions register(String email, String password) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/register")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body(email, password))));
  }

  private ResultActions verify(String token) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/verify-email")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("token", token))));
  }

  private ResultActions resend(String email) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/verify-email/resend")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", email))));
  }

  private static Map<String, Object> body(String email, String password) {
    return Map.of(
        "email",
        email,
        "password",
        password,
        "name",
        "Sam Parent",
        "phone",
        "+44 7700 900123",
        "confirmAdult",
        true,
        "acceptTerms",
        true);
  }

  private static String uniqueEmail() {
    return "parent-" + UUID.randomUUID() + "@example.com";
  }

  private long outboxCountFor(String email) {
    return jdbc.sql(
            "select count(*) from notification_outbox"
                + " where recipient_email = :email and type = 'VERIFY_EMAIL'")
        .param("email", email)
        .query(Long.class)
        .single();
  }

  /** The token in the newest verification email queued for this address. */
  private String queuedToken(String email) {
    String path =
        jdbc.sql(
                "select payload->>'path' from notification_outbox where recipient_email = :email"
                    + " order by created_at desc, id desc limit 1")
            .param("email", email)
            .query(String.class)
            .single();
    return tokenIn(path);
  }

  private Instant verifiedAt(String email) {
    return jdbc.sql("select email_verified_at from account where email = :email")
        .param("email", email)
        .query(Instant.class)
        .optional()
        .orElse(null);
  }

  private static String tokenIn(String text) {
    Matcher matcher = TOKEN.matcher(text);
    assertThat(matcher.find()).as("link in %s", text).isTrue();
    return matcher.group(1);
  }

  private static Map<String, Object> headersWithoutTraceId(MvcResult result) {
    Map<String, Object> headers = new java.util.TreeMap<>();
    for (String name : result.getResponse().getHeaderNames()) {
      if (!name.equalsIgnoreCase("X-Trace-Id")) {
        headers.put(name, result.getResponse().getHeaders(name));
      }
    }
    return headers;
  }
}
