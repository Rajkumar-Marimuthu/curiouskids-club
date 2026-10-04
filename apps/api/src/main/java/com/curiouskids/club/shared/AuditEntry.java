package com.curiouskids.club.shared;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One audit log entry. {@code details} must not contain personal data (names, emails, child
 * details); use IDs and codes instead.
 *
 * @param actorAccountId the account that acted, or {@code null} for the system
 * @param action what happened, for example {@code COPY_STATUS_CHANGED}
 * @param entityType the kind of thing acted on, for example {@code COPY}
 * @param entityId the thing acted on, or {@code null} when there is none
 * @param details extra facts as JSON-serialisable values
 */
public record AuditEntry(
    UUID actorAccountId,
    String action,
    String entityType,
    UUID entityId,
    Map<String, Object> details) {

  public AuditEntry {
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(entityType, "entityType");
    details =
        details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
  }
}
