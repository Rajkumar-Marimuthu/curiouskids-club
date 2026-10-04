package com.curiouskids.club.notification.internal.outbox;

import com.curiouskids.club.notification.Notifications;
import com.curiouskids.club.notification.OutboxEmail;
import com.curiouskids.club.shared.Ids;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Writes outbox rows with plain SQL in the caller's transaction. */
@Service
class JdbcNotifications implements Notifications {

  private final JdbcClient jdbc;
  private final JsonMapper json;
  private final Ids ids;
  private final Clock clock;

  JdbcNotifications(JdbcClient jdbc, JsonMapper json, Ids ids, Clock clock) {
    this.jdbc = jdbc;
    this.json = json;
    this.ids = ids;
    this.clock = clock;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean enqueue(OutboxEmail email) {
    Timestamp now = Timestamp.from(clock.instant());
    int inserted =
        jdbc.sql(
                """
                insert into notification_outbox
                  (id, type, recipient_account_id, recipient_email, payload, dedupe_key,
                   status, attempts, next_attempt_at, created_at, updated_at)
                values
                  (:id, :type, :accountId, :email, cast(:payload as jsonb), :dedupeKey,
                   'PENDING', 0, :now, :now, :now)
                on conflict (dedupe_key) do nothing
                """)
            .param("id", ids.next())
            .param("type", email.type().name())
            .param("accountId", email.recipientAccountId())
            .param("email", email.recipientEmail())
            .param("payload", json.writeValueAsString(email.payload()))
            .param("dedupeKey", email.dedupeKey())
            .param("now", now)
            .update();
    return inserted == 1;
  }

  @Override
  @Transactional(readOnly = true)
  public long failedCount() {
    return OutboxQueries.countByStatus(jdbc, OutboxStatus.FAILED);
  }
}
