package com.curiouskids.club.identity.internal.web;

import com.curiouskids.club.identity.CurrentUser;
import com.curiouskids.club.identity.internal.LoginService;
import com.curiouskids.club.shared.ProblemResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Log in and out, the current account, and the CSRF token (FR-ID-03, ADR-0004). */
@RestController
@Tag(name = "auth")
class SessionController {

  private static final String PROBLEM = "application/problem+json";

  private final LoginService logins;
  private final SessionLogin sessions;

  SessionController(LoginService logins, SessionLogin sessions) {
    this.logins = logins;
    this.sessions = sessions;
  }

  @PostMapping(path = "/api/v1/auth/login", produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(responseCode = "200", description = "Logged in; the session cookie is set")
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHENTICATED: email or password is incorrect",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "429",
      description = "RATE_LIMITED: too many failed attempts; see Retry-After",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  MeResponse login(
      @Valid @RequestBody LoginRequest login,
      HttpServletRequest request,
      HttpServletResponse response) {
    LoginService.Me me = logins.login(login.email(), login.password(), request.getRemoteAddr());
    sessions.start(me.user(), request, response);
    return toResponse(me);
  }

  @PostMapping("/api/v1/auth/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiResponse(responseCode = "204", description = "Logged out; the session no longer works")
  void logout(HttpServletRequest request, HttpServletResponse response) {
    sessions.end(request, response);
  }

  @GetMapping(path = "/api/v1/auth/me", produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(responseCode = "200", description = "The logged-in account")
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHENTICATED: not logged in",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  MeResponse me(@AuthenticationPrincipal CurrentUser user) {
    return toResponse(logins.me(user.accountId()));
  }

  /**
   * Sets the XSRF-TOKEN cookie. The web app calls it when the cookie is missing (first visit, after
   * login or logout) and sends the cookie's value in the X-XSRF-TOKEN header.
   */
  @GetMapping("/api/v1/auth/csrf")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiResponse(responseCode = "204", description = "The XSRF-TOKEN cookie is set")
  void csrf(@Parameter(hidden = true) CsrfToken token) {
    token.getToken(); // loading the deferred token writes the cookie
  }

  private static MeResponse toResponse(LoginService.Me me) {
    CurrentUser user = me.user();
    return new MeResponse(user.accountId(), user.role(), user.familyId(), me.emailVerified());
  }
}
