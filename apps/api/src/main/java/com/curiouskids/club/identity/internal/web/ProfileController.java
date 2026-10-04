package com.curiouskids.club.identity.internal.web;

import com.curiouskids.club.identity.CurrentUser;
import com.curiouskids.club.identity.internal.ProfileService;
import com.curiouskids.club.shared.ProblemResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in member's own profile, password, email and reminder preferences (FR-ID-06). */
@RestController
@Tag(name = "account")
class ProfileController {

  private static final String PROBLEM = "application/problem+json";

  private final ProfileService profiles;

  ProfileController(ProfileService profiles) {
    this.profiles = profiles;
  }

  @GetMapping(path = "/api/v1/me/profile", produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(responseCode = "200", description = "Your details and reminder preferences")
  ProfileResponse getProfile(@AuthenticationPrincipal CurrentUser user) {
    return toResponse(profiles.get(user.accountId()));
  }

  @PatchMapping(path = "/api/v1/me/profile", produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(responseCode = "200", description = "Profile changed")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_FAILED with field errors, including any field other than these",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  ProfileResponse updateProfile(
      @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ProfileUpdateRequest request) {
    return toResponse(
        profiles.update(
            user.accountId(),
            new ProfileService.ProfileChange(
                request.name(), request.phone(), request.remindPickup(), request.remindDueSoon())));
  }

  /** Keeps this session under a new ID and ends every other session of the account. */
  @PostMapping("/api/v1/me/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiResponse(
      responseCode = "204",
      description = "Password changed; your other sessions have ended")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_FAILED: current password wrong or new one too weak",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "429",
      description = "RATE_LIMITED: too many wrong passwords; see Retry-After",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  void changePassword(
      @AuthenticationPrincipal CurrentUser user,
      @Valid @RequestBody PasswordChangeRequest request,
      HttpServletRequest http) {
    HttpSession session = http.getSession(false);
    profiles.changePassword(
        user.accountId(),
        request.currentPassword(),
        request.newPassword(),
        session == null ? null : session.getId(),
        http.getRemoteAddr());
    if (session != null) {
      http.changeSessionId();
    }
  }

  /** The same 202 whether or not the new address already has an account. */
  @PostMapping("/api/v1/me/email")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @ApiResponse(
      responseCode = "202",
      description = "Accepted; a confirmation link is on its way to the new address")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_FAILED: current password wrong, or the address is not new",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "429",
      description = "RATE_LIMITED: too many wrong passwords or emailed links; see Retry-After",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  void requestEmailChange(
      @AuthenticationPrincipal CurrentUser user,
      @Valid @RequestBody EmailChangeRequest request,
      HttpServletRequest http) {
    profiles.requestEmailChange(
        user.accountId(), request.newEmail(), request.currentPassword(), http.getRemoteAddr());
  }

  private static ProfileResponse toResponse(ProfileService.Profile profile) {
    return new ProfileResponse(
        profile.name(),
        profile.phone(),
        profile.email(),
        profile.pendingEmail(),
        profile.remindPickup(),
        profile.remindDueSoon());
  }
}
