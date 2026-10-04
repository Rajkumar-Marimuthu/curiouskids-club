package com.curiouskids.club.identity.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** FR-ID-07: an invitation that was sent. */
record InvitationResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"VOLUNTEER", "ADMIN"})
        String role,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "When the link stops working")
        Instant expiresAt) {}
