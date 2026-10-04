package com.curiouskids.club.identity.internal.web;

import com.curiouskids.club.identity.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Component;

/** Starts and ends the server-side session for a login (ADR-0004). */
@Component
class SessionLogin {

  static final String LOGGED_IN_AT = "club.loggedInAt";
  static final Duration MEMBER_IDLE = Duration.ofDays(14);
  static final Duration STAFF_IDLE = Duration.ofMinutes(30);

  private final SecurityContextRepository contexts;
  private final CsrfTokenRepository csrfTokens;
  private final Clock clock;

  SessionLogin(SecurityContextRepository contexts, CsrfTokenRepository csrfTokens, Clock clock) {
    this.contexts = contexts;
    this.csrfTokens = csrfTokens;
    this.clock = clock;
  }

  /** Puts the user in a session with a new ID, so a session fixed before login is useless. */
  void start(CurrentUser user, HttpServletRequest request, HttpServletResponse response) {
    request.getSession(true);
    request.changeSessionId();
    HttpSession session = request.getSession();
    session.setMaxInactiveInterval((int) (user.isStaff() ? STAFF_IDLE : MEMBER_IDLE).toSeconds());
    session.setAttribute(LOGGED_IN_AT, clock.instant());
    session.setAttribute(
        FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, user.getName());

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))));
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, request, response);
    // A token seen before login is not trusted after it; the web app fetches a new one.
    csrfTokens.saveToken(null, request, response);
  }

  /** Deletes the session, so its cookie no longer works anywhere. */
  void end(HttpServletRequest request, HttpServletResponse response) {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
    csrfTokens.saveToken(null, request, response);
  }
}
