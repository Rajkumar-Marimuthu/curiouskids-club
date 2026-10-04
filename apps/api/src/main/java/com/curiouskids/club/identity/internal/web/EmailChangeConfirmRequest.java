package com.curiouskids.club.identity.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** FR-ID-06: the token from the link sent to the new address. */
record EmailChangeConfirmRequest(@NotBlank @Size(min = 1, max = 100) String token) {}
