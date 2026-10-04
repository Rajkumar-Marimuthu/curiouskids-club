package com.curiouskids.club.notification.internal.outbox;

/** Lifecycle of an outbox row: PENDING until sent, or FAILED after the last attempt. */
enum OutboxStatus {
  PENDING,
  SENT,
  FAILED
}
