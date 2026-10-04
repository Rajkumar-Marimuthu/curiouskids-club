package com.curiouskids.club.identity.internal.web;

import com.curiouskids.club.identity.CurrentUser;
import com.curiouskids.club.identity.internal.StaffInvitationService;
import com.curiouskids.club.shared.ProblemResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admins invite volunteers and admins (FR-ID-07, BR-31). Admin only. */
@RestController
@Tag(name = "admin")
class InvitationController {

  private static final String PROBLEM = "application/problem+json";

  private final StaffInvitationService invitations;

  InvitationController(StaffInvitationService invitations) {
    this.invitations = invitations;
  }

  /** A newer invitation for the same email replaces the older one. */
  @PostMapping(path = "/api/v1/admin/invitations", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @ApiResponse(responseCode = "201", description = "Invitation sent")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_FAILED with field errors",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "CONFLICT: the email already has an account",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  InvitationResponse invite(
      @AuthenticationPrincipal CurrentUser admin, @Valid @RequestBody InvitationRequest request) {
    StaffInvitationService.Invitation invitation =
        invitations.invite(admin.accountId(), request.email(), request.role());
    return new InvitationResponse(
        invitation.id(), invitation.email(), invitation.role(), invitation.expiresAt());
  }
}
