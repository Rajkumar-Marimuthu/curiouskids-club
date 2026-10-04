package com.curiouskids.club.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * An account holder asked to reset their password (FR-ID-04). Published inside the requesting
 * transaction, so a listener that queues the email commits or rolls back with it.
 *
 * @param accountId the account
 * @param email where to send the link
 * @param tokenId identifies this link, for de-duplication
 * @param token the single-use secret for the link; only its hash is stored
 * @param expiresAt when the link stops working
 */
public record PasswordResetRequested(
    UUID accountId, String email, UUID tokenId, String token, Instant expiresAt) {

  @Override
  public String toString() {
    // Never print the address or the token.
    return "PasswordResetRequested[accountId=" + accountId + ", tokenId=" + tokenId + "]";
  }
}
