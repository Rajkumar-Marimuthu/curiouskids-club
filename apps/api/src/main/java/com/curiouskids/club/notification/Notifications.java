package com.curiouskids.club.notification;

/** Queues transactional emails for reliable delivery (FR-NOT-03). */
public interface Notifications {

  /**
   * Queues an email in the caller's transaction, so it is sent only if the business change commits
   * and is sent even if the application restarts before sending. Must be called inside a
   * transaction.
   *
   * @return true if queued, false if an email with the same dedupe key was already queued
   */
  boolean enqueue(OutboxEmail email);

  /** Emails that failed every attempt and need an admin's attention (FR-ADM-02). */
  long failedCount();
}
