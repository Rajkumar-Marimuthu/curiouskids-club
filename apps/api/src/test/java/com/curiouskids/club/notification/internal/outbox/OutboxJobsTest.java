package com.curiouskids.club.notification.internal.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.curiouskids.club.notification.EmailType;
import com.curiouskids.club.notification.Notifications;
import com.curiouskids.club.notification.OutboxEmail;
import com.curiouskids.club.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs the real schedule, so the context is closed afterwards to stop its jobs. */
@TestPropertySource(
    properties = {"club.jobs.enabled=true", "club.notification.poll-interval=PT0.2S"})
@DirtiesContext
class OutboxJobsTest extends IntegrationTest {

  @Autowired Notifications notifications;
  @Autowired TransactionTemplate transaction;
  @Autowired JdbcClient jdbc;

  @Test
  @DisplayName("FR-NOT-03: the scheduled job sends queued emails under a ShedLock lock")
  void scheduledJobSendsQueuedEmail() {
    String to = "parent-" + UUID.randomUUID() + "@example.com";
    transaction.executeWithoutResult(
        status ->
            notifications.enqueue(
                new OutboxEmail(
                    EmailType.STAFF_INVITATION,
                    null,
                    to,
                    Map.of("path", "/invitation?token=xyz"),
                    "STAFF_INVITATION:" + to)));

    assertThat(mailpit().awaitMessagesTo(to, 1).getFirst().subject())
        .isEqualTo("You're invited to help run Curiouskids Club");
    assertThat(
            jdbc.sql("select count(*) from shedlock where name = 'notification-outbox-dispatch'")
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }
}
