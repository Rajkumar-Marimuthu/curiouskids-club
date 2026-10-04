package com.curiouskids.club.support;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Sends a CSRF token the way the web app does: the {@code XSRF-TOKEN} cookie echoed in the {@code
 * X-XSRF-TOKEN} header (ADR-0004).
 *
 * <p>Used instead of Spring Security's {@code csrf()} post-processor, which swaps the application's
 * cookie token repository for a session-based one in the shared filter chain and so would hide the
 * real configuration from every later test.
 */
public final class Csrf {

  private Csrf() {}

  public static RequestPostProcessor csrf() {
    return request -> {
      String token = UUID.randomUUID().toString();
      Cookie[] existing = request.getCookies() == null ? new Cookie[0] : request.getCookies();
      Cookie[] cookies = Arrays.copyOf(existing, existing.length + 1);
      cookies[existing.length] = new Cookie("XSRF-TOKEN", token);
      request.setCookies(cookies);
      request.addHeader("X-XSRF-TOKEN", token);
      return request;
    };
  }
}
