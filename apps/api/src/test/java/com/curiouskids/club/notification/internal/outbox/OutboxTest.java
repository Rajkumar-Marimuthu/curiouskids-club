package com.curiouskids.club.notification.internal.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.curiouskids.club.notification.EmailType;
import com.curiouskids.club.notification.Notifications;
import com.curiouskids.club.notification.OutboxEmail;
import com.curiouskids.club.support.FaultInjectingEmailSender;
import com.curiouskids.club.support.IntegrationTest;
import com.curiouskids.club.support.Mailpit;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(OutputCaptureExtension.class)
class OutboxTest extends IntegrationTest {

  @Autowired Notifications notifications;
  @Autowired OutboxDispatcher dispatcher;
  @Autowired OutboxRetention retention;
  @Autowired FaultInjectingEmailSender sender;
  @Autowired TransactionTemplate transaction;
  @Autowired JdbcClient jdbc;

  private final Mailpit mailpit = mailpit();

  @BeforeEach
  void emptyOutbox() {
    jdbc.sql("delete from notification_outbox").update();
    sender.reset();
  }

  @AfterEach
  void providerWorksAgain() {
    sender.reset();
  }

  @Test
  @DisplayName("FR-NOT-03: an email queued with the business change is sent by a later run")
  void queuedEmailIsSentByLaterRun() {
    String to = uniqueAddress();
    // The business transaction commits with the row; nothing is sent yet.
    boolean queued = enqueueCommitted(verifyEmail(to));

    assertThat(queued).isTrue();
    assertThat(statusOf(to)).isEqualTo("PENDING");
    assertThat(sender.attempts()).isZero();

    // Any later run, in this or a restarted instance, finds the row in the database and sends it.
    assertThat(dispatcher.dispatchDue()).isEqualTo(1);

    Mailpit.Message message = mailpit.awaitMessagesTo(to, 1).getFirst();
    assertThat(message.subject()).isEqualTo("Please confirm your email for Curiouskids Club");
    assertThat(message.from()).isEqualTo("noreply@curiouskids.example");
    assertThat(statusOf(to)).isEqualTo("SENT");
    assertThat(dispatcher.dispatchDue()).isZero();
  }

  @Test
  @DisplayName("FR-NOT-01: the email has an HTML body and a plain-text alternative with the link")
  void emailHasTextAlternative() {
    String to = uniqueAddress();
    transaction.executeWithoutResult(
        status -> notifications.enqueue(email(EmailType.PASSWORD_RESET, to, "/reset?token=abc")));

    dispatcher.dispatchDue();

    Mailpit.Message message = mailpit.awaitMessagesTo(to, 1).getFirst();
    assertThat(message.html()).contains("href=\"http://localhost:5173/reset?token=abc\"");
    assertThat(message.text())
        .contains("Reset your password")
        .contains("http://localhost:5173/reset?token=abc")
        .doesNotContain("<");
  }

  @Test
  @DisplayName("FR-NOT-03: an email queued in a transaction that rolls back is never sent")
  void rolledBackEmailIsNotQueued() {
    String to = uniqueAddress();
    transaction.executeWithoutResult(
        status -> {
          notifications.enqueue(verifyEmail(to));
          status.setRollbackOnly();
        });

    assertThat(dispatcher.dispatchDue()).isZero();
    assertThat(countFor(to)).isZero();
  }

  @Test
  @DisplayName("FR-NOT-03: queueing outside a business transaction is refused")
  void enqueueNeedsTransaction() {
    assertThatThrownBy(() -> notifications.enqueue(verifyEmail(uniqueAddress())))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  @DisplayName("DB-09: an email with the same dedupe key is queued and sent only once")
  void sameDedupeKeyIsQueuedOnce() {
    String to = uniqueAddress();
    OutboxEmail email = verifyEmail(to);

    assertThat(enqueueCommitted(email)).isTrue();
    dispatcher.dispatchDue();
    // A retry or job re-run queues the same email again, before and after it was sent.
    assertThat(enqueueCommitted(email)).isFalse();
    dispatcher.dispatchDue();

    assertThat(countFor(to)).isEqualTo(1);
    assertThat(sender.attempts()).isEqualTo(1);
    assertThat(mailpit.awaitMessagesTo(to, 1)).hasSize(1);
  }

  @Test
  @DisplayName("DB-09: the database rejects a second row with the same dedupe key")
  void databaseEnforcesUniqueDedupeKey() {
    String key = "TEST:" + UUID.randomUUID();
    insertRaw(key);

    assertThatThrownBy(() -> insertRaw(key)).isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("FR-NOT-03: a failed send is retried after 1, 5, 15 and 60 minutes, then FAILED")
  void failedSendBacksOffThenFails(CapturedOutput output) {
    String to = uniqueAddress();
    transaction.executeWithoutResult(status -> notifications.enqueue(verifyEmail(to)));
    sender.failing(true);

    List<Duration> waits = new ArrayList<>();
    for (int attempt = 1; attempt <= 4; attempt++) {
      assertThat(dispatcher.dispatchDue()).isZero();
      assertThat(statusOf(to)).isEqualTo("PENDING");
      assertThat(attemptsOf(to)).isEqualTo(attempt);
      waits.add(scheduledWait(to));
      // Not due yet: another run leaves it alone.
      dispatcher.dispatchDue();
      assertThat(attemptsOf(to)).isEqualTo(attempt);
      makeDue(to);
    }
    assertThat(dispatcher.dispatchDue()).isZero();

    assertThat(waits)
        .containsExactly(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofMinutes(60));
    assertThat(statusOf(to)).isEqualTo("FAILED");
    assertThat(attemptsOf(to)).isEqualTo(5);
    assertThat(notifications.failedCount()).isEqualTo(1);
    assertThat(sender.attempts()).isEqualTo(5);

    // A FAILED row is never picked up again.
    sender.failing(false);
    makeDue(to);
    assertThat(dispatcher.dispatchDue()).isZero();

    assertThat(output).contains("Outbox email failed for good").doesNotContain(to);
  }

  @Test
  @DisplayName("FR-NOT-03: a send that succeeds on retry is marked SENT")
  void retrySucceeds() {
    String to = uniqueAddress();
    transaction.executeWithoutResult(status -> notifications.enqueue(verifyEmail(to)));
    sender.failing(true);
    dispatcher.dispatchDue();

    sender.failing(false);
    makeDue(to);

    assertThat(dispatcher.dispatchDue()).isEqualTo(1);
    assertThat(statusOf(to)).isEqualTo("SENT");
    assertThat(attemptsOf(to)).isEqualTo(2);
    assertThat(mailpit.awaitMessagesTo(to, 1)).hasSize(1);
  }

  @Test
  @DisplayName("FR-NOT-03: concurrent dispatchers send each queued email exactly once")
  void concurrentDispatchersSendEachEmailOnce() throws Exception {
    String domain = UUID.randomUUID() + ".example";
    int emails = 40;
    transaction.executeWithoutResult(
        status -> {
          for (int i = 0; i < emails; i++) {
            notifications.enqueue(verifyEmail("parent" + i + "@" + domain));
          }
        });

    int threads = 4;
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      Callable<Integer> run =
          () -> {
            start.await();
            return dispatcher.dispatchDue();
          };
      for (int i = 0; i < threads; i++) {
        results.add(pool.submit(run));
      }
      start.countDown();
      int sent = 0;
      for (Future<Integer> result : results) {
        sent += result.get();
      }
      assertThat(sent).isEqualTo(emails);
    }

    assertThat(sender.attempts()).isEqualTo(emails);
    assertThat(
            jdbc.sql("select count(*) from notification_outbox where status = 'SENT'")
                .query(Long.class)
                .single())
        .isEqualTo(emails);
    for (int i = 0; i < emails; i++) {
      assertThat(mailpit.awaitMessagesTo("parent" + i + "@" + domain, 1)).hasSize(1);
    }
  }

  @Test
  @DisplayName("FR-NOT-03: the address and values of emails sent over 30 days ago are cleared")
  void retentionClearsOldSentDetails() {
    String old = uniqueAddress();
    String recent = uniqueAddress();
    String pending = uniqueAddress();
    transaction.executeWithoutResult(
        status -> {
          notifications.enqueue(verifyEmail(old));
          notifications.enqueue(verifyEmail(recent));
        });
    dispatcher.dispatchDue();
    transaction.executeWithoutResult(status -> notifications.enqueue(verifyEmail(pending)));
    sentDaysAgo(old, 31);
    sentDaysAgo(recent, 29);

    assertThat(retention.clearSentDetails()).isEqualTo(1);

    assertThat(countFor(old)).isZero();
    assertThat(countFor(recent)).isEqualTo(1);
    assertThat(countFor(pending)).isEqualTo(1);
    assertThat(
            jdbc.sql(
                    "select count(*) from notification_outbox"
                        + " where recipient_email is null and payload = '{}'::jsonb")
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  private boolean enqueueCommitted(OutboxEmail email) {
    return Boolean.TRUE.equals(transaction.execute(status -> notifications.enqueue(email)));
  }

  private static String uniqueAddress() {
    return "parent-" + UUID.randomUUID() + "@example.com";
  }

  private static OutboxEmail verifyEmail(String to) {
    return email(EmailType.VERIFY_EMAIL, to, "/verify-email?token=" + UUID.randomUUID());
  }

  private static OutboxEmail email(EmailType type, String to, String path) {
    return new OutboxEmail(type, null, to, Map.of("path", path), type + ":" + to);
  }

  private String statusOf(String to) {
    return jdbc.sql("select status from notification_outbox where recipient_email = :to")
        .param("to", to)
        .query(String.class)
        .single();
  }

  private int attemptsOf(String to) {
    return jdbc.sql("select attempts from notification_outbox where recipient_email = :to")
        .param("to", to)
        .query(Integer.class)
        .single();
  }

  private long countFor(String to) {
    return jdbc.sql("select count(*) from notification_outbox where recipient_email = :to")
        .param("to", to)
        .query(Long.class)
        .single();
  }

  /** The wait the dispatcher scheduled, measured from when it recorded the failure. */
  private Duration scheduledWait(String to) {
    return jdbc.sql(
            "select updated_at, next_attempt_at from notification_outbox where recipient_email = :to")
        .param("to", to)
        .query(
            (rs, n) ->
                Duration.between(
                    rs.getObject("updated_at", OffsetDateTime.class),
                    rs.getObject("next_attempt_at", OffsetDateTime.class)))
        .single();
  }

  private void makeDue(String to) {
    jdbc.sql(
            "update notification_outbox set next_attempt_at = now() - interval '1 second'"
                + " where recipient_email = :to")
        .param("to", to)
        .update();
  }

  private void sentDaysAgo(String to, int days) {
    jdbc.sql(
            "update notification_outbox set sent_at = now() - make_interval(days => :days)"
                + " where recipient_email = :to")
        .param("days", days)
        .param("to", to)
        .update();
  }

  private void insertRaw(String dedupeKey) {
    jdbc.sql(
            """
            insert into notification_outbox
              (id, type, recipient_email, dedupe_key, next_attempt_at, created_at, updated_at)
            values (gen_random_uuid(), 'VERIFY_EMAIL', 'x@example.com', :key, now(), now(), now())
            """)
        .param("key", dedupeKey)
        .update();
  }
}
