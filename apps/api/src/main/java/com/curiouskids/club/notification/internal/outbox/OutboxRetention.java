package com.curiouskids.club.notification.internal.outbox;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clears the address and template values from rows sent more than 30 days ago
 * (security-and-privacy.md). The row stays, so its dedupe key still prevents a resend.
 */
@Component
class OutboxRetention {

  static final Duration KEEP_SENT_DETAILS = Duration.ofDays(30);

  private final JdbcClient jdbc;
  private final Clock clock;

  OutboxRetention(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** Returns how many rows were cleared. */
  @Transactional
  int clearSentDetails() {
    Instant now = clock.instant();
    return jdbc.sql(
            """
            update notification_outbox
            set recipient_email = null, payload = '{}'::jsonb, updated_at = :now
            where status = 'SENT' and sent_at < :cutoff and recipient_email is not null
            """)
        .param("now", Timestamp.from(now))
        .param("cutoff", Timestamp.from(now.minus(KEEP_SENT_DETAILS)))
        .update();
  }
}
