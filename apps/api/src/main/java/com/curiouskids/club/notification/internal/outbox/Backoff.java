package com.curiouskids.club.notification.internal.outbox;

import java.time.Duration;
import java.util.List;

/** FR-NOT-03: retry with backoff up to 5 attempts, then FAILED. */
final class Backoff {

  static final int MAX_ATTEMPTS = 5;

  // Wait after failed attempt 1, 2, 3 and 4.
  private static final List<Duration> DELAYS =
      List.of(
          Duration.ofMinutes(1),
          Duration.ofMinutes(5),
          Duration.ofMinutes(15),
          Duration.ofMinutes(60));

  /** The wait before the next attempt, given how many attempts have failed (1 to 4). */
  static Duration after(int failedAttempts) {
    return DELAYS.get(failedAttempts - 1);
  }

  private Backoff() {}
}
