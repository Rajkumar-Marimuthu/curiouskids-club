package com.curiouskids.club.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * A member needs an email to confirm their address (FR-ID-02). Published inside the registering
 * transaction, so a listener that queues the email commits or rolls back with it.
 *
 * @param accountId the member's account
 * @param email where to send the link
 * @param tokenId identifies this link, for de-duplication
 * @param token the single-use secret for the link; only its hash is stored
 * @param expiresAt when the link stops working
 */
public record EmailVerificationRequested(
    UUID accountId, String email, UUID tokenId, String token, Instant expiresAt) {

  @Override
  public String toString() {
    // Never print the address or the token.
    return "EmailVerificationRequested[accountId=" + accountId + ", tokenId=" + tokenId + "]";
  }
}
