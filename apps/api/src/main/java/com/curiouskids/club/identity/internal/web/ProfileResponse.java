package com.curiouskids.club.identity.internal.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** FR-ID-06: the member's own details and reminder preferences. */
record ProfileResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(description = "Absent if none was given") @JsonInclude(JsonInclude.Include.NON_NULL)
        String phone,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
    @Schema(description = "A new address waiting for its confirmation link to be used")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String pendingEmail,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Pickup reminders (BR-32) are sent")
        boolean remindPickup,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Due-soon and due-today reminders (BR-33) are sent")
        boolean remindDueSoon) {}
