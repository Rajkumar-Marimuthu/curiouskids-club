package com.curiouskids.club.identity.internal.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** FR-ID-06: the new address, confirmed by the current password. */
record EmailChangeRequest(
    @NotBlank @Email @Size(min = 1, max = 254) String newEmail,
    @NotBlank @Size(min = 1, max = 128) String currentPassword) {}
