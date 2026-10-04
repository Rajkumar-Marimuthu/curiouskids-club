package com.curiouskids.club.notification;

import java.util.Set;

/**
 * The transactional emails the club sends (FR-NOT-01). Each type has an HTML and a plain-text
 * template under {@code templates/email/} and declares the payload values its templates use. Later
 * tasks add their types here together with their templates.
 */
public enum EmailType {

  /** T-011: confirm a new member's email address. */
  VERIFY_EMAIL(Set.of("path")),

  /** T-012: reset a forgotten password. */
  PASSWORD_RESET(Set.of("path")),

  /** T-013: confirm a new email address before the account moves to it. */
  EMAIL_CHANGE(Set.of("path")),

  /** T-013: tell the previous address that the account's email changed. */
  EMAIL_CHANGED(Set.of()),

  /** T-014: invite a volunteer or admin to create their account. */
  STAFF_INVITATION(Set.of("path"));

  private final Set<String> payloadKeys;

  EmailType(Set<String> payloadKeys) {
    this.payloadKeys = payloadKeys;
  }

  /** The payload values the templates need, all required. */
  public Set<String> payloadKeys() {
    return payloadKeys;
  }
}
