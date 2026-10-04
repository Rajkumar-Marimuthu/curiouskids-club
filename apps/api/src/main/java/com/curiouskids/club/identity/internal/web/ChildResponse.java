package com.curiouskids.club.identity.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** A child profile (FR-ID-05). */
record ChildResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String firstName,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"0-2", "3-5", "6-8", "9-12", "13+"})
        String ageBand) {}
