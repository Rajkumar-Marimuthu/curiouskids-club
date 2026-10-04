package com.curiouskids.club.identity;

import java.util.UUID;

/**
 * A member confirmed a new email address (FR-ID-06). The previous address is told, so an unwanted
 * change does not go unnoticed. Published inside the confirming transaction.
 *
 * @param accountId the account
 * @param previousEmail the address the account had before the change
 * @param tokenId the confirmation link that was used, for de-duplication
 */
public record EmailChanged(UUID accountId, String previousEmail, UUID tokenId) {

  @Override
  public String toString() {
    // Never print the address.
    return "EmailChanged[accountId=" + accountId + ", tokenId=" + tokenId + "]";
  }
}
