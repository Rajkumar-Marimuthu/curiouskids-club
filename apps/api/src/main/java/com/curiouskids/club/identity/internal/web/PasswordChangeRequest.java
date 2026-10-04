package com.curiouskids.club.identity.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** FR-ID-06: the current password, and a new one checked like at registration. */
record PasswordChangeRequest(
    @NotBlank @Size(min = 1, max = 128) String currentPassword,
    @NotBlank @Size(min = 12, max = 128) String newPassword) {}
