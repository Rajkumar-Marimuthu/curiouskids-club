package com.curiouskids.club.notification.internal.outbox;

import org.springframework.jdbc.core.simple.JdbcClient;

final class OutboxQueries {

  static long countByStatus(JdbcClient jdbc, OutboxStatus status) {
    return jdbc.sql("select count(*) from notification_outbox where status = :status")
        .param("status", status.name())
        .query(Long.class)
        .single();
  }

  private OutboxQueries() {}
}
