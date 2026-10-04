package com.curiouskids.club.identity.internal.web;

import static com.curiouskids.club.support.Csrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.curiouskids.club.support.IntegrationTest;
import com.curiouskids.club.support.TestAccounts;
import com.curiouskids.club.support.TestAccounts.Account;
import com.curiouskids.testsupport.AreaProbeController;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@Import(AreaProbeController.class)
@ExtendWith(OutputCaptureExtension.class)
class SessionTest extends IntegrationTest {

  private static final String SESSION_COOKIE = "__Host-SESSION";

  @Autowired TestAccounts accounts;
  @Autowired JdbcClient jdbc;
  @Autowired JsonMapper json;

  @BeforeEach
  void clean() {
    jdbc.sql("delete from login_failure").update();
    jdbc.sql("delete from spring_session").update();
  }

  @Test
  @DisplayName(
      "FR-ID-03: verified credentials create an HttpOnly session and return role and family")
  void loginCreatesSession() throws Exception {
    Account member = accounts.member(true);

    MvcResult result =
        login(member.email(), TestAccounts.PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(member.id().toString()))
            .andExpect(jsonPath("$.role").value("MEMBER"))
            .andExpect(jsonPath("$.familyId").value(member.familyId().toString()))
            .andExpect(jsonPath("$.emailVerified").value(true))
            .andReturn();

    String setCookie =
        result.getResponse().getHeaders("Set-Cookie").stream()
            .filter(c -> c.startsWith(SESSION_COOKIE + "="))
            .findFirst()
            .orElseThrow();
    assertThat(setCookie)
        .contains("Path=/")
        .contains("Secure")
        .contains("HttpOnly")
        .contains("SameSite=Lax")
        .doesNotContain("Domain");
    Cookie session = sessionCookie(result);
    mvc.perform(get("/api/v1/auth/me").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(member.id().toString()));
    // The session row names the account by ID, never by email.
    assertThat(jdbc.sql("select principal_name from spring_session").query(String.class).single())
        .isEqualTo(member.id().toString());
  }

  @Test
  @DisplayName("FR-ID-03: an unverified member can log in; the response says so")
  void unverifiedCanLogIn() throws Exception {
    Account member = accounts.member(false);

    login(member.email(), TestAccounts.PASSWORD)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.emailVerified").value(false));
  }

  @Test
  @DisplayName("FR-ID-03: the email is matched without regard to case or surrounding spaces")
  void emailIsNormalised() throws Exception {
    Account member = accounts.member(true);

    login("  " + member.email().toUpperCase() + " ", TestAccounts.PASSWORD)
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("FR-ID-03: wrong password, unknown email and disabled account all get one message")
  void failuresAreIndistinguishable() throws Exception {
    Account member = accounts.member(true);
    Account disabled = accounts.member(true);
    accounts.disable(disabled);

    String wrongPassword = failureBody(login(member.email(), "not the right password"));
    String unknown = failureBody(login("nobody-" + member.id() + "@example.com", "whatever"));
    String disabledAccount = failureBody(login(disabled.email(), TestAccounts.PASSWORD));

    assertThat(wrongPassword).isEqualTo(unknown).isEqualTo(disabledAccount);
    assertThat(wrongPassword)
        .contains("UNAUTHENTICATED")
        .contains("Email or password is incorrect.");
  }

  @Test
  @DisplayName("FR-ID-03: 5 failures in 15 minutes for one account block it with a generic message")
  void fiveFailuresBlockTheAccount() throws Exception {
    Account member = accounts.member(true);
    for (int i = 0; i < 5; i++) {
      login(member.email(), "wrong password " + i).andExpect(status().isUnauthorized());
    }

    // Even the right password is refused now, with Retry-After of up to 15 minutes.
    MvcResult blocked =
        login(member.email(), TestAccounts.PASSWORD)
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
            .andExpect(header().exists("Retry-After"))
            .andReturn();
    assertThat(Long.parseLong(blocked.getResponse().getHeader("Retry-After")))
        .isBetween(890L, 900L);

    // An unknown email is blocked the same way, so blocking reveals nothing.
    String unknown = "nobody-" + member.id() + "@example.com";
    for (int i = 0; i < 5; i++) {
      login(unknown, "wrong").andExpect(status().isUnauthorized());
    }
    login(unknown, "wrong").andExpect(status().isTooManyRequests());

    // Once the failures are older than 15 minutes, the account can log in again.
    jdbc.sql("update login_failure set at = at - interval '15 minutes'").update();
    login(member.email(), TestAccounts.PASSWORD).andExpect(status().isOk());
  }

  @Test
  @DisplayName("FR-ID-03: 20 failures in 15 minutes from one address block that address")
  void twentyFailuresBlockTheAddress() throws Exception {
    for (int i = 0; i < 20; i++) {
      login("someone-" + i + "@example.com", "wrong").andExpect(status().isUnauthorized());
    }
    Account member = accounts.member(true);

    login(member.email(), TestAccounts.PASSWORD).andExpect(status().isTooManyRequests());
  }

  @Test
  @DisplayName("FR-ID-03: a successful login clears that email's earlier failures")
  void successClearsFailures() throws Exception {
    Account member = accounts.member(true);
    for (int i = 0; i < 4; i++) {
      login(member.email(), "wrong").andExpect(status().isUnauthorized());
    }
    login(member.email(), TestAccounts.PASSWORD).andExpect(status().isOk());

    login(member.email(), "wrong").andExpect(status().isUnauthorized());
    login(member.email(), TestAccounts.PASSWORD).andExpect(status().isOk());
  }

  @Test
  @DisplayName("FR-ID-03: failed logins store only hashes and are purged after a day")
  void failuresStoreHashesOnly() throws Exception {
    Account member = accounts.member(true);
    login(member.email(), "wrong");

    Map<String, Object> row =
        jdbc.sql("select email_hash, ip_hash from login_failure").query().singleRow();
    assertThat(row.values()).allSatisfy(v -> assertThat((String) v).matches("[0-9a-f]{64}"));
    assertThat(row.values().toString()).doesNotContain(member.email()).doesNotContain("127.0.0.1");
  }

  @Test
  @DisplayName("FR-ID-03: logout ends the session, so the old cookie no longer works")
  void logoutEndsSession() throws Exception {
    Account member = accounts.member(true);
    Cookie session = sessionCookie(login(member.email(), TestAccounts.PASSWORD).andReturn());

    mvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(session))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/auth/me").cookie(session))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    assertThat(jdbc.sql("select count(*) from spring_session").query(Long.class).single()).isZero();
  }

  @Test
  @DisplayName("FR-ID-03: login issues a new session ID, so a planted session cannot be reused")
  void loginRotatesSessionId() throws Exception {
    Account member = accounts.member(true);
    Cookie first = sessionCookie(login(member.email(), TestAccounts.PASSWORD).andReturn());

    Cookie second =
        sessionCookie(
            mvc.perform(
                    post("/api/v1/auth/login")
                        .with(csrf())
                        .cookie(first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(member.email(), TestAccounts.PASSWORD)))
                .andExpect(status().isOk())
                .andReturn());

    assertThat(second.getValue()).isNotEqualTo(first.getValue());
    mvc.perform(get("/api/v1/auth/me").cookie(first)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("FR-ID-03: members stay logged in for 14 idle days, staff for 30 idle minutes")
  void idleTimeoutsByRole() throws Exception {
    login(accounts.member(true).email(), TestAccounts.PASSWORD);
    login(accounts.volunteer().email(), TestAccounts.PASSWORD);

    assertThat(
            jdbc.sql("select max_inactive_interval from spring_session order by 1")
                .query(Integer.class)
                .list())
        .containsExactly(30 * 60, 14 * 24 * 60 * 60);
  }

  @Test
  @DisplayName("FR-ID-03: an expired session is refused")
  void expiredSessionRefused() throws Exception {
    Cookie session =
        sessionCookie(login(accounts.volunteer().email(), TestAccounts.PASSWORD).andReturn());
    jdbc.sql(
            "update spring_session set last_access_time = last_access_time - (max_inactive_interval + 1) * 1000")
        .update();

    mvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("FR-ID-07: each area admits only its roles; others get 403, anonymous gets 401")
  void areasAdmitTheirRoles() throws Exception {
    Cookie member = loginAs(accounts.member(true));
    Cookie volunteer = loginAs(accounts.volunteer());
    Cookie admin = loginAs(accounts.admin());

    expectStatus("/api/v1/me/probe", null, 401);
    expectStatus("/api/v1/me/probe", member, 200);
    expectStatus("/api/v1/staff/probe", null, 401);
    expectStatus("/api/v1/staff/probe", member, 403);
    expectStatus("/api/v1/staff/probe", volunteer, 200);
    expectStatus("/api/v1/staff/probe", admin, 200);
    expectStatus("/api/v1/admin/probe", member, 403);
    expectStatus("/api/v1/admin/probe", volunteer, 403);
    expectStatus("/api/v1/admin/probe", admin, 200);
    mvc.perform(get("/api/v1/admin/probe").cookie(volunteer))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("NFR-06: login logs contain no email or password")
  void loginLogsHoldNoPersonalData(CapturedOutput output) throws Exception {
    Account member = accounts.member(true);
    login(member.email(), "wrong password here");
    login(member.email(), TestAccounts.PASSWORD);

    assertThat(output)
        .contains("Login failed")
        .contains("Logged in")
        .doesNotContain(member.email())
        .doesNotContain(TestAccounts.PASSWORD)
        .doesNotContain("wrong password here");
  }

  @Test
  @DisplayName("NFR-05: responses carry the security headers from security-and-privacy.md")
  void securityHeaders() throws Exception {
    mvc.perform(get("/api/v1/ping"))
        .andExpect(
            header().string("Content-Security-Policy", SecurityConfig.CONTENT_SECURITY_POLICY))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
        .andExpect(header().string("Permissions-Policy", SecurityConfig.PERMISSIONS_POLICY))
        .andExpect(header().string("X-Frame-Options", "DENY"));
  }

  @Test
  @DisplayName("ADR-0004: the CSRF endpoint sets the XSRF-TOKEN cookie the web app echoes")
  void csrfEndpoint() throws Exception {
    Cookie cookie =
        mvc.perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse()
            .getCookie("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    assertThat(cookie.isHttpOnly()).isFalse();

    mvc.perform(
            post("/api/v1/auth/logout").cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue()))
        .andExpect(status().isNoContent());
    mvc.perform(post("/api/v1/auth/logout").cookie(cookie).header("X-XSRF-TOKEN", "forged"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName(
      "ADR-0004: login and logout clear the CSRF token, so a token from before is not reused")
  void loginAndLogoutRotateCsrfToken() throws Exception {
    Account member = accounts.member(true);
    MvcResult loggedIn = login(member.email(), TestAccounts.PASSWORD).andReturn();
    assertThat(loggedIn.getResponse().getCookie("XSRF-TOKEN").getMaxAge()).isZero();

    MvcResult loggedOut =
        mvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(sessionCookie(loggedIn)))
            .andReturn();
    assertThat(loggedOut.getResponse().getCookie("XSRF-TOKEN").getMaxAge()).isZero();
  }

  private ResultActions login(String email, String password) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/login")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(credentials(email, password)));
  }

  private Cookie loginAs(Account account) throws Exception {
    return sessionCookie(login(account.email(), TestAccounts.PASSWORD).andReturn());
  }

  private void expectStatus(String path, Cookie session, int status) throws Exception {
    var request = get(path);
    if (session != null) {
      request.cookie(session);
    }
    mvc.perform(request).andExpect(status().is(status));
  }

  private String credentials(String email, String password) {
    return json.writeValueAsString(Map.of("email", email, "password", password));
  }

  private static String failureBody(ResultActions result) throws Exception {
    String body =
        result.andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
    // Trace IDs differ per request; everything else must match.
    return body.replaceAll("\"traceId\":\"[^\"]*\"", "");
  }

  private static Cookie sessionCookie(MvcResult result) {
    Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
    assertThat(cookie).as("session cookie").isNotNull();
    return cookie;
  }
}
