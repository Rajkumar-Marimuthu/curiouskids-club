package com.curiouskids.club.identity.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * FR-ID-01. Both consent boxes must be ticked (BR-28). The password's strength is checked in the
 * service; only its length is declared here.
 */
record RegisterRequest(
    @NotBlank @Email @Size(min = 1, max = 254) String email,
    @NotBlank @Size(min = 12, max = 128) String password,
    @NotBlank @Size(min = 1, max = 100) String name,
    @Size(max = 30) @Pattern(regexp = "^[0-9+()\\- ]*$", message = "Use digits, spaces, + ( ) -")
        String phone,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "The parent confirms they are 18 or older (BR-28)")
        @NotNull(message = "Please confirm you are 18 or older")
        @AssertTrue(message = "Please confirm you are 18 or older")
        Boolean confirmAdult,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "The parent accepts the terms and privacy notice (BR-28)")
        @NotNull(message = "Please accept the terms and privacy notice")
        @AssertTrue(message = "Please accept the terms and privacy notice")
        Boolean acceptTerms) {}
