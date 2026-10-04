package com.curiouskids.club.notification.internal.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.curiouskids.club.support.IntegrationTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class OutboxMetricsTest extends IntegrationTest {

  @Autowired OutboxMetrics metrics;
  @Autowired JdbcClient jdbc;

  @BeforeEach
  void emptyOutbox() {
    jdbc.sql("delete from notification_outbox").update();
  }

  @Test
  @DisplayName("FR-NOT-03: pending and failed outbox emails are exposed for the FAILED alarm")
  void gaugesCountPendingAndFailed() {
    insert("PENDING");
    insert("PENDING");
    insert("FAILED");
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    metrics.bindTo(registry);

    assertThat(registry.get("club.outbox.emails").tag("status", "pending").gauge().value())
        .isEqualTo(2);
    assertThat(registry.get("club.outbox.emails").tag("status", "failed").gauge().value())
        .isEqualTo(1);
  }

  private void insert(String status) {
    jdbc.sql(
            """
            insert into notification_outbox
              (id, type, recipient_email, dedupe_key, status, attempts, next_attempt_at,
               created_at, updated_at)
            values (gen_random_uuid(), 'VERIFY_EMAIL', 'x@example.com', gen_random_uuid()::text,
                    :status, 0, now(), now(), now())
            """)
        .param("status", status)
        .update();
  }
}
