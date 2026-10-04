package com.curiouskids.club.identity.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** FR-ID-07: the token from the invitation link and a password, checked like at registration. */
record InvitationAcceptRequest(
    @NotBlank @Size(min = 1, max = 100) String token,
    @NotBlank @Size(min = 12, max = 128) String password) {}
