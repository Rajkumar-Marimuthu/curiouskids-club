package com.curiouskids.club.identity.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** FR-ID-05: a child's first name or nickname and age band; nothing else is accepted (BR-30). */
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record ChildRequest(
    @NotBlank @Size(min = 1, max = 40) String firstName,
    @Schema(allowableValues = {"0-2", "3-5", "6-8", "9-12", "13+"})
        @NotNull
        @Pattern(regexp = ChildRequest.AGE_BANDS, message = "Choose an age band")
        String ageBand) {

  static final String AGE_BANDS = "0-2|3-5|6-8|9-12|13\\+";
}
