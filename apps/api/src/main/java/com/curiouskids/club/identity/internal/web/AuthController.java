package com.curiouskids.club.identity.internal.web;

import com.curiouskids.club.identity.internal.RegistrationService;
import com.curiouskids.club.shared.ProblemResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public registration and email verification (FR-ID-01, FR-ID-02). */
@RestController
@Tag(name = "auth")
class AuthController {

  private static final String PROBLEM = "application/problem+json";

  private final RegistrationService registration;

  AuthController(RegistrationService registration) {
    this.registration = registration;
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
  void register(@Valid @RequestBody RegisterRequest request) {
    registration.register(
        new RegistrationService.Registration(
            request.email(), request.password(), request.name(), request.phone()));
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
  void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
    registration.resendVerification(request.email());
  }
}
