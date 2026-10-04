package com.curiouskids.club.notification.internal.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.Locale;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Outbox depth for monitoring: {@code club.outbox.emails{status=pending|failed}}. The alarm "any
 * outbox row FAILED" (deployment-and-operations.md) watches the failed gauge.
 */
@Component
class OutboxMetrics implements MeterBinder {

  private final JdbcClient jdbc;

  OutboxMetrics(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    for (OutboxStatus status : new OutboxStatus[] {OutboxStatus.PENDING, OutboxStatus.FAILED}) {
      Gauge.builder("club.outbox.emails", () -> OutboxQueries.countByStatus(jdbc, status))
          .tag("status", status.name().toLowerCase(Locale.ROOT))
          .description("Outbox emails by status")
          .register(registry);
    }
  }
}
