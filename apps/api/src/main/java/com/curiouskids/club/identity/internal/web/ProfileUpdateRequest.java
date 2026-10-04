package com.curiouskids.club.identity.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * FR-ID-06: the fields to change; an absent field is left as it is, and an empty phone removes it.
 * Overdue reminders cannot be switched off (BR-36). Nothing else is accepted.
 */
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record ProfileUpdateRequest(
    @Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        String name,
    @Size(max = 30) @Pattern(regexp = "^[0-9+()\\- ]*$", message = "Use digits, spaces, + ( ) -")
        String phone,
    Boolean remindPickup,
    Boolean remindDueSoon) {}
