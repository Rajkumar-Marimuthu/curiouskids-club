package com.curiouskids.club.identity.internal.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** The logged-in account: role and family (FR-ID-03), and whether reserving is allowed yet. */
record MeResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID accountId,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"MEMBER", "VOLUNTEER", "ADMIN"})
        String role,
    @Schema(description = "Absent for staff") @JsonInclude(JsonInclude.Include.NON_NULL)
        UUID familyId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean emailVerified) {}
