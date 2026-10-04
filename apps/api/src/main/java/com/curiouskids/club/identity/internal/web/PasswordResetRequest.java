package com.curiouskids.club.identity.internal.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** FR-ID-04: where to send a reset link. */
record PasswordResetRequest(@NotBlank @Email @Size(min = 1, max = 254) String email) {}
