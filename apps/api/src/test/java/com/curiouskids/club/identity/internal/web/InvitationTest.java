package com.curiouskids.club.identity.internal.web;

import static com.curiouskids.club.support.Csrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.curiouskids.club.identity.internal.StaffInvitationService;
import com.curiouskids.club.notification.internal.outbox.OutboxTestSupport;
import com.curiouskids.club.support.IntegrationTest;
import com.curiouskids.club.support.Mailpit;
import com.curiouskids.club.support.TestAccounts;
import com.curiouskids.club.support.TestAccounts.Account;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class InvitationTest extends IntegrationTest {

  private static final String PASSWORD = "orange penguins dance quietly";
  private static final Pattern TOKEN =
      Pattern.compile("accept-invitation\\?token=([A-Za-z0-9_-]+)");

  @Autowired TestAccounts accounts;
  @Autowired StaffInvitationService invitations;
  @Autowired JdbcClient jdbc;
  @Autowired JsonMapper json;

  private final Mailpit mailpit = mailpit();

  @Test
  @DisplayName("FR-ID-07: an admin invites a volunteer; a 7-day single-use link is emailed")
  void inviteSendsLink() throws Exception {
    Account admin = accounts.admin();
    String email = newEmail();

    invite(admin.session(), email.toUpperCase(), "VOLUNTEER")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.role").value("VOLUNTEER"))
        .andExpect(jsonPath("$.expiresAt").exists());

    Map<String, Object> stored =
        jdbc.sql(
                """
                select role, invited_by, expires_at - created_at = interval '7 days' as seven_days
                from staff_invitation where email = :email
                """)
            .param("email", email)
            .query()
            .singleRow();
    assertThat(stored)
        .containsEntry("role", "VOLUNTEER")
        .containsEntry("invited_by", admin.id())
        .containsEntry("seven_days", true);
    OutboxTestSupport.dispatch(context);
    Mailpit.Message message = mailpit.awaitMessagesTo(email, 1).getFirst();
    assertThat(message.subject()).isEqualTo("You're invited to help run Curiouskids Club");
    assertThat(message.text()).containsPattern(TOKEN);
  }

  @Test
  @DisplayName("FR-ID-07: accepting creates a verified VOLUNTEER account with no family")
  void acceptCreatesVolunteer() throws Exception {
    Account admin = accounts.admin();
    String email = newEmail();
    String token = inviteToken(admin, email, "VOLUNTEER");

    accept(token, PASSWORD).andExpect(status().isNoContent());

    Map<String, Object> account =
        jdbc.sql(
                """
                select role, family_id, email_verified_at is not null as verified, consent_at
                from account where email = :email
                """)
            .param("email", email)
            .query()
            .singleRow();
    assertThat(account)
        .containsEntry("role", "VOLUNTEER")
        .containsEntry("family_id", null)
        .containsEntry("verified", true)
        .containsEntry("consent_at", null);
    Cookie session = login(email, PASSWORD);
    mvc.perform(get("/api/v1/auth/me").cookie(session))
        .andExpect(jsonPath("$.role").value("VOLUNTEER"))
        .andExpect(jsonPath("$.familyId").doesNotExist())
        .andExpect(jsonPath("$.emailVerified").value(true));
    expectTokenInvalid(accept(token, "another long password here"));
  }

  @Test
  @DisplayName("FR-ID-07: an admin can invite another admin")
  void inviteAdmin() throws Exception {
    String email = newEmail();

    accept(inviteToken(accounts.admin(), email, "ADMIN"), PASSWORD)
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/auth/me").cookie(login(email, PASSWORD)))
        .andExpect(jsonPath("$.role").value("ADMIN"));
  }

  @Test
  @DisplayName("FR-ADM-03: invitations and acceptances are audited with IDs and the role only")
  void invitationsAreAudited() throws Exception {
    Account admin = accounts.admin();
    String email = newEmail();
    accept(inviteToken(admin, email, "VOLUNTEER"), PASSWORD).andExpect(status().isNoContent());
    UUID invitationId =
        jdbc.sql("select id from staff_invitation where email = :email")
            .param("email", email)
            .query(UUID.class)
            .single();
    UUID accountId =
        jdbc.sql("select id from account where email = :email")
            .param("email", email)
            .query(UUID.class)
            .single();

    List<Map<String, Object>> entries =
        jdbc.sql(
                """
                select actor_account_id, action, entity_type, details::text as details
                from audit_log where entity_id = :id order by at, action desc
                """)
            .param("id", invitationId)
            .query()
            .listOfRows();

    assertThat(entries).hasSize(2);
    assertThat(entries.get(0))
        .containsEntry("actor_account_id", admin.id())
        .containsEntry("action", "STAFF_INVITED")
        .containsEntry("entity_type", "STAFF_INVITATION");
    assertThat(entries.get(1))
        .containsEntry("actor_account_id", accountId)
        .containsEntry("action", "STAFF_INVITATION_ACCEPTED");
    assertThat(entries)
        .allSatisfy(
            entry ->
                assertThat((String) entry.get("details"))
                    .contains("VOLUNTEER")
                    .doesNotContain(email));
  }

  @Test
  @DisplayName("FR-ID-07: a newer invitation for the same email replaces the older link")
  void newerInvitationReplacesOlder() throws Exception {
    Account admin = accounts.admin();
    String email = newEmail();
    String first = inviteToken(admin, email, "VOLUNTEER");
    String second = inviteToken(admin, email, "ADMIN");

    expectTokenInvalid(accept(first, PASSWORD));
    accept(second, PASSWORD).andExpect(status().isNoContent());

    assertThat(
            jdbc.sql("select role from account where email = :email")
                .param("email", email)
                .query(String.class)
                .single())
        .isEqualTo("ADMIN");
  }

  @Test
  @DisplayName("FR-ID-07: a link older than 7 days no longer works")
  void expiredLinkRefused() throws Exception {
    String email = newEmail();
    String token = inviteToken(accounts.admin(), email, "VOLUNTEER");
    jdbc.sql(
            "update staff_invitation set expires_at = now() - interval '1 second' where email = :e")
        .param("e", email)
        .update();

    expectTokenInvalid(accept(token, PASSWORD));
  }

  @Test
  @DisplayName("FR-ID-07: a weak password is refused with a field error and keeps the link")
  void weakPasswordRefused() throws Exception {
    String token = inviteToken(accounts.admin(), newEmail(), "VOLUNTEER");

    accept(token, "password1234")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("password"));

    accept(token, PASSWORD).andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("FR-ID-07: inviting an email that already has an account is refused with CONFLICT")
  void existingAccountRefused() throws Exception {
    Account member = accounts.member(true);

    invite(accounts.admin().session(), member.email(), "VOLUNTEER")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(jsonPath("$.errors[0].field").value("email"));

    assertThat(
            jdbc.sql("select count(*) from staff_invitation where email = :e")
                .param("e", member.email())
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  @DisplayName("FR-ID-07: if the email gains an account before the link is used, it fails")
  void emailTakenBeforeAccept() throws Exception {
    String email = newEmail();
    String token = inviteToken(accounts.admin(), email, "VOLUNTEER");
    jdbc.sql("update account set email = :email where id = :id")
        .param("email", email)
        .param("id", accounts.member(true).id())
        .update();

    expectTokenInvalid(accept(token, PASSWORD));
  }

  @Test
  @DisplayName("FR-ID-07: an unknown role or a missing email gets a field error")
  void invalidInvitationRefused() throws Exception {
    Account admin = accounts.admin();

    invite(admin.session(), newEmail(), "MEMBER")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("role"));
    invite(admin.session(), "not an email", "VOLUNTEER")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("email"));
  }

  @Test
  @DisplayName("FR-ID-07: volunteers and members get 403 on inviting; anonymous callers 401")
  void onlyAdminsInvite() throws Exception {
    invite(accounts.volunteer().session(), newEmail(), "VOLUNTEER")
        .andExpect(status().isForbidden());
    invite(accounts.member(true).session(), newEmail(), "VOLUNTEER")
        .andExpect(status().isForbidden());
    invite(request -> request, newEmail(), "VOLUNTEER").andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("FR-ID-07: the same link used twice at once creates one account")
  void concurrentAcceptsCreateOneAccount() throws Exception {
    String email = newEmail();
    String token = inviteToken(accounts.admin(), email, "VOLUNTEER");

    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
      for (int i = 0; i < 4; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return accept(token, PASSWORD).andReturn().getResponse().getStatus();
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
                .param("email", email)
                .query(Long.class)
                .single())
        .isOne();
  }

  @Test
  @DisplayName("T-014: the first admin is invited at start-up only while the club has no admin")
  void firstAdminInvitation() throws Exception {
    // Other tests' admins would count; this test needs a club without one.
    jdbc.sql("update account set role = 'VOLUNTEER' where role = 'ADMIN'").update();
    String email = newEmail();

    assertThat(invitations.inviteFirstAdmin(email)).isTrue();
    assertThat(invitations.inviteFirstAdmin(email)).as("link still works").isFalse();
    jdbc.sql(
            "update staff_invitation set expires_at = now() - interval '1 second' where email = :e")
        .param("e", email)
        .update();
    assertThat(invitations.inviteFirstAdmin(email)).as("link expired").isTrue();

    String path =
        jdbc.sql(
                """
                select payload ->> 'path' from notification_outbox
                where recipient_email = :e and type = 'STAFF_INVITATION'
                order by created_at desc limit 1
                """)
            .param("e", email)
            .query(String.class)
            .single();
    accept(token(path), PASSWORD).andExpect(status().isNoContent());
    assertThat(invitations.inviteFirstAdmin(newEmail())).as("an admin exists").isFalse();
    assertThat(
            jdbc.sql(
                    "select actor_account_id is null from audit_log"
                        + " where action = 'STAFF_INVITED' and entity_id ="
                        + " (select id from staff_invitation where email = :e and accepted_at is not null)")
                .param("e", email)
                .query(Boolean.class)
                .single())
        .isTrue();
  }

  @Test
  @DisplayName("NFR-06: invitation logs contain no email or token")
  void noPersonalDataLogged(CapturedOutput output) throws Exception {
    String email = newEmail();
    String token = inviteToken(accounts.admin(), email, "VOLUNTEER");
    accept(token, PASSWORD).andExpect(status().isNoContent());

    assertThat(output)
        .contains("Staff invited")
        .contains("Staff invitation accepted")
        .doesNotContain(email)
        .doesNotContain(token)
        .doesNotContain(PASSWORD);
  }

  private static String newEmail() {
    return "staff-" + UUID.randomUUID() + "@example.com";
  }

  /** Invites and returns the token from the queued email. */
  private String inviteToken(Account admin, String email, String role) throws Exception {
    invite(admin.session(), email, role).andExpect(status().isCreated());
    String path =
        jdbc.sql(
                """
                select payload ->> 'path' from notification_outbox
                where recipient_email = :e and type = 'STAFF_INVITATION'
                order by created_at desc limit 1
                """)
            .param("e", email)
            .query(String.class)
            .single();
    return token(path);
  }

  private static String token(String path) {
    Matcher matcher = TOKEN.matcher(path);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private ResultActions invite(RequestPostProcessor as, String email, String role)
      throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.post("/api/v1/admin/invitations")
            .with(as)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", email, "role", role))));
  }

  private ResultActions accept(String token, String password) throws Exception {
    return post("/api/v1/auth/invitations/accept", Map.of("token", token, "password", password));
  }

  private Cookie login(String email, String password) throws Exception {
    Cookie cookie =
        post("/api/v1/auth/login", Map.of("email", email, "password", password))
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
