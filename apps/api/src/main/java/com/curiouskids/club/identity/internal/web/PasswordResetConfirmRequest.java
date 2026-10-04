package com.curiouskids.club.identity.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** FR-ID-04: the token from the emailed link and the new password, checked like at registration. */
record PasswordResetConfirmRequest(
    @NotBlank @Size(min = 1, max = 100) String token,
    @NotBlank @Size(min = 12, max = 128) String password) {}
