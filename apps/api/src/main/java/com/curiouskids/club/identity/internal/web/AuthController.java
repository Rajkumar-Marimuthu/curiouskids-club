package com.curiouskids.club.identity.internal.web;

import com.curiouskids.club.identity.internal.PasswordResetService;
import com.curiouskids.club.identity.internal.RegistrationService;
import com.curiouskids.club.shared.ProblemResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public registration, email verification and password reset (FR-ID-01, FR-ID-02, FR-ID-04). */
@RestController
@Tag(name = "auth")
class AuthController {

  private static final String PROBLEM = "application/problem+json";

  private final RegistrationService registration;
  private final PasswordResetService passwordReset;

  AuthController(RegistrationService registration, PasswordResetService passwordReset) {
    this.registration = registration;
    this.passwordReset = passwordReset;
  }

  /** The same 202 whether or not the email is already registered. */
  @PostMapping("/api/v1/auth/register")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @ApiResponse(responseCode = "202", description = "Accepted; check your email")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_FAILED with field errors",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "429",
      description = "RATE_LIMITED: too many requests; see Retry-After",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  void register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
    registration.register(
        new RegistrationService.Registration(
            request.email(), request.password(), request.name(), request.phone()),
        http.getRemoteAddr());
  }

  @PostMapping("/api/v1/auth/verify-email")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiResponse(responseCode = "204", description = "Email verified")
  @ApiResponse(
      responseCode = "400",
      description = "TOKEN_INVALID or VALIDATION_FAILED",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
    registration.verifyEmail(request.token());
  }

  /** The same 202 whether or not a link was sent. */
  @PostMapping("/api/v1/auth/verify-email/resend")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @ApiResponse(responseCode = "202", description = "Accepted; check your email")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_FAILED with field errors",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "429",
      description = "RATE_LIMITED: too many requests; see Retry-After",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  void resendVerification(
      @Valid @RequestBody ResendVerificationRequest request, HttpServletRequest http) {
    registration.resendVerification(request.email(), http.getRemoteAddr());
  }

  /** The same 202 whether or not the email is registered. */
  @PostMapping("/api/v1/auth/password-reset/request")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @ApiResponse(responseCode = "202", description = "Accepted; check your email")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_FAILED with field errors",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "429",
      description = "RATE_LIMITED: too many requests; see Retry-After",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  void requestPasswordReset(
      @Valid @RequestBody PasswordResetRequest request, HttpServletRequest http) {
    passwordReset.request(request.email(), http.getRemoteAddr());
  }

  @PostMapping("/api/v1/auth/password-reset/confirm")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiResponse(
      responseCode = "204",
      description = "Password changed; every session of the account has ended")
  @ApiResponse(
      responseCode = "400",
      description = "TOKEN_INVALID, or VALIDATION_FAILED with field errors",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
    passwordReset.confirm(request.token(), request.password());
  }
}
