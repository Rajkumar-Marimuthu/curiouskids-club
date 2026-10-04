package com.curiouskids.club.notification.internal.outbox;

import com.curiouskids.club.notification.EmailType;
import com.curiouskids.club.notification.internal.email.EmailSender;
import com.curiouskids.club.notification.internal.email.EmailTemplates;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends due outbox rows (FR-NOT-03). Each row is claimed with {@code FOR UPDATE SKIP LOCKED} in its
 * own transaction, so concurrent dispatchers never send the same row twice and a crash leaves the
 * row PENDING to be sent on the next run. Logs carry the row ID and type only, never the address.
 */
@Component
class OutboxDispatcher {

  /** Bounds one run so a large backlog cannot hold the job lock for long. */
  static final int MAX_PER_RUN = 200;

  private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
  private static final TypeReference<Map<String, String>> PAYLOAD = new TypeReference<>() {};

  private final JdbcClient jdbc;
  private final TransactionTemplate transaction;
  private final EmailTemplates templates;
  private final EmailSender sender;
  private final JsonMapper json;
  private final Clock clock;

  OutboxDispatcher(
      JdbcClient jdbc,
      TransactionTemplate transaction,
      EmailTemplates templates,
      EmailSender sender,
      JsonMapper json,
      Clock clock) {
    this.jdbc = jdbc;
    this.transaction = transaction;
    this.templates = templates;
    this.sender = sender;
    this.json = json;
    this.clock = clock;
  }

  /**
   * Sends every due row, up to {@link #MAX_PER_RUN}.
   *
   * @return how many emails were sent
   */
  int dispatchDue() {
    int sent = 0;
    for (int i = 0; i < MAX_PER_RUN; i++) {
      Optional<Boolean> outcome = transaction.execute(status -> dispatchNext());
      if (outcome == null || outcome.isEmpty()) {
        break;
      }
      if (outcome.get()) {
        sent++;
      }
    }
    return sent;
  }

  /** Empty when nothing is due; otherwise whether the claimed row was sent. */
  private Optional<Boolean> dispatchNext() {
    Instant now = clock.instant();
    Optional<Row> claimed =
        jdbc.sql(
                """
                select id, type, recipient_email, payload::text as payload, attempts
                from notification_outbox
                where status = 'PENDING' and next_attempt_at <= :now
                order by next_attempt_at, id
                limit 1
                for update skip locked
                """)
            .param("now", Timestamp.from(now))
            .query(
                (rs, n) ->
                    new Row(
                        rs.getObject("id", UUID.class),
                        EmailType.valueOf(rs.getString("type")),
                        rs.getString("recipient_email"),
                        rs.getString("payload"),
                        rs.getInt("attempts")))
            .optional();
    if (claimed.isEmpty()) {
      return Optional.empty();
    }
    Row row = claimed.get();
    try {
      sender.send(
          templates.render(
              row.type(), row.recipientEmail(), json.readValue(row.payload(), PAYLOAD)));
    } catch (RuntimeException e) {
      recordFailure(row, e, now);
      return Optional.of(false);
    }
    jdbc.sql(
            """
            update notification_outbox
            set status = 'SENT', attempts = attempts + 1, sent_at = :now, last_error = null,
                updated_at = :now
            where id = :id
            """)
        .param("id", row.id())
        .param("now", Timestamp.from(now))
        .update();
    log.atInfo()
        .addKeyValue("outboxId", row.id())
        .addKeyValue("emailType", row.type())
        .log("Outbox email sent");
    return Optional.of(true);
  }

  private void recordFailure(Row row, RuntimeException e, Instant now) {
    int attempts = row.attempts() + 1;
    boolean giveUp = attempts >= Backoff.MAX_ATTEMPTS;
    Instant next = giveUp ? now : now.plus(Backoff.after(attempts));
    jdbc.sql(
            """
            update notification_outbox
            set status = :status, attempts = :attempts, next_attempt_at = :next,
                last_error = :error, updated_at = :now
            where id = :id
            """)
        .param("id", row.id())
        .param("status", (giveUp ? OutboxStatus.FAILED : OutboxStatus.PENDING).name())
        .param("attempts", attempts)
        .param("next", Timestamp.from(next))
        .param("error", e.getClass().getName())
        .param("now", Timestamp.from(now))
        .update();
    // The exception message can contain the address, so log its type only.
    log.atWarn()
        .addKeyValue("outboxId", row.id())
        .addKeyValue("emailType", row.type())
        .addKeyValue("attempts", attempts)
        .addKeyValue("error", e.getClass().getName())
        .log(giveUp ? "Outbox email failed for good" : "Outbox email failed, will retry");
  }

  private record Row(
      UUID id, EmailType type, String recipientEmail, String payload, int attempts) {}
}
