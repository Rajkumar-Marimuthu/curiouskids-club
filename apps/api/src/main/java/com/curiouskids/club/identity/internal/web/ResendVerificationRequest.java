package com.curiouskids.club.identity.internal.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** FR-ID-02: where to send a new verification link. */
record ResendVerificationRequest(@NotBlank @Email @Size(min = 1, max = 254) String email) {}
