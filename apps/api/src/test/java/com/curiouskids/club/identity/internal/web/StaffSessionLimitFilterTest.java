package com.curiouskids.club.identity.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.curiouskids.club.identity.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class StaffSessionLimitFilterTest {

  private static final Instant LOGGED_IN = Instant.parse("2026-10-05T08:00:00Z");

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("FR-ID-03: a staff session ends 8 hours after login however active it is")
  void staffSessionEndsAfterEightHours() throws Exception {
    MockHttpSession session = run("VOLUNTEER", LOGGED_IN.plusSeconds(8 * 3600));

    assertThat(session.isInvalid()).isTrue();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("FR-ID-03: a staff session is kept until the 8 hours are up")
  void staffSessionKeptBeforeEightHours() throws Exception {
    MockHttpSession session = run("ADMIN", LOGGED_IN.plusSeconds(8 * 3600 - 1));

    assertThat(session.isInvalid()).isFalse();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
  }

  @Test
  @DisplayName("FR-ID-03: member sessions have no absolute limit")
  void memberSessionHasNoAbsoluteLimit() throws Exception {
    MockHttpSession session = run("MEMBER", LOGGED_IN.plusSeconds(10 * 24 * 3600));

    assertThat(session.isInvalid()).isFalse();
  }

  private static MockHttpSession run(String role, Instant now) throws Exception {
    CurrentUser user = new CurrentUser(UUID.randomUUID(), role, null);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    MockHttpSession session = new MockHttpSession();
    session.setAttribute(SessionLogin.LOGGED_IN_AT, LOGGED_IN);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
    request.setSession(session);
    MockFilterChain chain = new MockFilterChain();

    new StaffSessionLimitFilter(Clock.fixed(now, ZoneOffset.UTC))
        .doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).as("request continues").isNotNull();
    return session;
  }
}
