package com.curiouskids.club.identity.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** FR-ID-02: the token from the emailed link. */
record VerifyEmailRequest(@NotBlank @Size(min = 1, max = 100) String token) {}
