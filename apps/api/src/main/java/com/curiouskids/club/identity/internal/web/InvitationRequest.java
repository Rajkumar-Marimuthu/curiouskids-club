package com.curiouskids.club.identity.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** FR-ID-07: who to invite, and as which staff role (BR-31). */
record InvitationRequest(
    @NotBlank @Email @Size(min = 1, max = 254) String email,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"VOLUNTEER", "ADMIN"})
        @NotNull(message = "Choose a role")
        @Pattern(regexp = "VOLUNTEER|ADMIN", message = "Choose a role")
        String role) {}
