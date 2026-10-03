package com.curiouskids.club.shared;

/**
 * Append-only record of staff and system actions (FR-ADM-03). Entries join the caller's
 * transaction, so they are written only if the action commits.
 */
public interface AuditLog {

  void record(AuditEntry entry);
}
