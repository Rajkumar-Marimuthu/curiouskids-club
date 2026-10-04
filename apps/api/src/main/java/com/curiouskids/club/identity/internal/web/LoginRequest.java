package com.curiouskids.club.identity.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** FR-ID-03. No format rules beyond length, so a typo gets the same answer as a wrong password. */
record LoginRequest(
    @NotBlank @Size(min = 1, max = 254) String email,
    @NotBlank @Size(min = 1, max = 128) String password) {}
