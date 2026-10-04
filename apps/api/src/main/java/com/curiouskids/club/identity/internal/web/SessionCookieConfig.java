package com.curiouskids.club.identity.internal.web;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * The session cookie (ADR-0004, security-and-privacy.md). Defined here rather than through {@code
 * server.servlet.session.cookie.*}, which Spring Boot applies only with an embedded server, so the
 * cookie is the same in tests and in every deployment.
 */
@Configuration(proxyBeanMethods = false)
class SessionCookieConfig {

  static final String COOKIE_NAME = "__Host-SESSION";

  /** Survives browser restarts; the server still expires idle sessions (14 days or 30 minutes). */
  static final Duration COOKIE_MAX_AGE = Duration.ofDays(14);

  @Bean
  CookieSerializer cookieSerializer() {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName(COOKIE_NAME);
    serializer.setCookiePath("/"); // a __Host- cookie has path / and no Domain
    serializer.setUseHttpOnlyCookie(true);
    serializer.setUseSecureCookie(true);
    serializer.setSameSite("Lax");
    serializer.setCookieMaxAge((int) COOKIE_MAX_AGE.toSeconds());
    return serializer;
  }
}
