package com.curiouskids.club.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * A member asked to move their account to a new email address (FR-ID-06). Published inside the
 * requesting transaction, so a listener that queues the email commits or rolls back with it.
 *
 * @param accountId the account
 * @param newEmail where to send the confirmation link
 * @param tokenId identifies this link, for de-duplication
 * @param token the single-use secret for the link; only its hash is stored
 * @param expiresAt when the link stops working
 */
public record EmailChangeRequested(
    UUID accountId, String newEmail, UUID tokenId, String token, Instant expiresAt) {

  @Override
  public String toString() {
    // Never print the address or the token.
    return "EmailChangeRequested[accountId=" + accountId + ", tokenId=" + tokenId + "]";
  }
}
