package com.curiouskids.club.shared.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record PingResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant time) {}
