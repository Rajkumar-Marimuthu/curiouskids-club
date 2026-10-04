package com.curiouskids.club.notification;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * An email to queue in the outbox.
 *
 * <p>Payload values are inserted into the template, so they must come from the system (paths with
 * tokens, dates, title names from the catalogue), never from text a member typed. A {@code path}
 * value is a path in the web app starting with {@code /}; the template turns it into a full link.
 *
 * @param type which email
 * @param recipientAccountId the recipient's account, or null when they have none yet (invitations)
 * @param recipientEmail where to send it
 * @param payload the values {@link EmailType#payloadKeys()} names, and no others
 * @param dedupeKey unique per recipient, type and subject, so the email is queued at most once
 *     (DB-09), for example {@code VERIFY_EMAIL:<token id>}
 */
public record OutboxEmail(
    EmailType type,
    UUID recipientAccountId,
    String recipientEmail,
    Map<String, String> payload,
    String dedupeKey) {

  public OutboxEmail {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(recipientEmail, "recipientEmail");
    Objects.requireNonNull(dedupeKey, "dedupeKey");
    payload = Map.copyOf(Objects.requireNonNull(payload, "payload"));
    if (!payload.keySet().equals(type.payloadKeys())) {
      throw new IllegalArgumentException(
          type + " needs payload keys " + type.payloadKeys() + ", got " + payload.keySet());
    }
    if (recipientEmail.isBlank() || dedupeKey.isBlank() || dedupeKey.length() > 200) {
      throw new IllegalArgumentException("recipientEmail and dedupeKey are required");
    }
    String path = payload.get("path");
    if (path != null && !(path.startsWith("/") && !path.startsWith("//"))) {
      throw new IllegalArgumentException("path must be a path in the web app");
    }
  }
}
