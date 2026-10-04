package com.curiouskids.club.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * An admin invited someone to create a staff account (FR-ID-07). Published inside the inviting
 * transaction, so a listener that queues the email commits or rolls back with it.
 *
 * @param invitationId identifies this invitation, for de-duplication
 * @param email where to send the link
 * @param role the role the account will have: VOLUNTEER or ADMIN
 * @param token the single-use secret for the link; only its hash is stored
 * @param expiresAt when the link stops working
 */
public record StaffInvited(
    UUID invitationId, String email, String role, String token, Instant expiresAt) {

  @Override
  public String toString() {
    // Never print the address or the token.
    return "StaffInvited[invitationId=" + invitationId + ", role=" + role + "]";
  }
}
