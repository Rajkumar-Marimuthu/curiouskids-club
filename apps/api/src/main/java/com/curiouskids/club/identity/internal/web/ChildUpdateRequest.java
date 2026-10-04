package com.curiouskids.club.identity.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** FR-ID-05: the fields to change; an absent field is left as it is. Nothing else is accepted. */
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record ChildUpdateRequest(
    @Size(min = 1, max = 40) @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        String firstName,
    @Schema(allowableValues = {"0-2", "3-5", "6-8", "9-12", "13+"})
        @Pattern(regexp = ChildRequest.AGE_BANDS, message = "Choose an age band")
        String ageBand) {}
