package com.curiouskids.club.identity.internal.web;

import static com.curiouskids.club.shared.ErrorCode.FORBIDDEN;
import static com.curiouskids.club.shared.ErrorCode.UNAUTHENTICATED;

import com.curiouskids.club.shared.Problems;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

/**
 * Deny by default (security-and-privacy.md, ADR-0004): only the endpoints listed here are public;
 * {@code /staff} needs VOLUNTEER or ADMIN and {@code /admin} needs ADMIN. Sessions live in
 * PostgreSQL (Spring Session JDBC). State-changing requests need the CSRF token from the {@code
 * XSRF-TOKEN} cookie in the {@code X-XSRF-TOKEN} header. Errors are problem+json.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

  static final String CONTENT_SECURITY_POLICY =
      "default-src 'self'; img-src 'self' data:; script-src 'self'; style-src 'self';"
          + " frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

  static final String PERMISSIONS_POLICY = "camera=(self), microphone=(), geolocation=()";

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  /** The XSRF-TOKEN cookie, readable by the web app, which echoes it in X-XSRF-TOKEN. */
  @Bean
  CsrfTokenRepository csrfTokenRepository() {
    CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repository.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Lax"));
    return repository;
  }

  @Bean
  SecurityFilterChain apiSecurity(
      HttpSecurity http,
      Problems problems,
      SecurityContextRepository contexts,
      CsrfTokenRepository csrfTokens,
      Clock clock)
      throws Exception {
    http.securityContext(context -> context.securityContextRepository(contexts))
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokens))
        .requestCache(cache -> cache.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(HttpMethod.GET, "/api/v1/ping", "/api/v1/auth/csrf")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/verify-email",
                        "/api/v1/auth/verify-email/resend",
                        "/api/v1/auth/password-reset/request",
                        "/api/v1/auth/password-reset/confirm",
                        "/api/v1/auth/login",
                        "/api/v1/auth/logout")
                    .permitAll()
                    .requestMatchers(
                        "/actuator/health/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml")
                    .permitAll()
                    .requestMatchers("/api/v1/me/**")
                    .hasRole("MEMBER")
                    .requestMatchers("/api/v1/staff/**")
                    .hasAnyRole("VOLUNTEER", "ADMIN")
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, e) ->
                            problems.write(response, UNAUTHENTICATED, "Please log in."))
                    .accessDeniedHandler(
                        (request, response, e) ->
                            problems.write(response, FORBIDDEN, "You are not allowed to do this.")))
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                    .referrerPolicy(
                        referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    .addHeaderWriter(
                        new StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY)))
        .addFilterAfter(new StaffSessionLimitFilter(clock), SecurityContextHolderFilter.class);
    return http.build();
  }
}
