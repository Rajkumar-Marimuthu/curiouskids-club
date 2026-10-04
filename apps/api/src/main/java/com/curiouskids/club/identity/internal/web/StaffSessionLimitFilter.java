package com.curiouskids.club.identity.internal.web;

import com.curiouskids.club.identity.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ends staff sessions 8 hours after login however active they are (security-and-privacy.md). The
 * idle limit is the session's own max inactive interval. Not a bean, so it runs only inside the
 * security filter chain.
 */
class StaffSessionLimitFilter extends OncePerRequestFilter {

  static final Duration STAFF_ABSOLUTE = Duration.ofHours(8);

  private final Clock clock;

  StaffSessionLimitFilter(Clock clock) {
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    HttpSession session = request.getSession(false);
    if (session != null && session.getAttribute(SessionLogin.LOGGED_IN_AT) instanceof Instant at) {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication != null
          && authentication.getPrincipal() instanceof CurrentUser user
          && user.isStaff()
          && !clock.instant().isBefore(at.plus(STAFF_ABSOLUTE))) {
        session.invalidate();
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }
}
