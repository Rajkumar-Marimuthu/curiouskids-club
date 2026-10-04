package com.curiouskids.club.shared.internal;

import com.curiouskids.club.shared.AuditEntry;
import com.curiouskids.club.shared.AuditLog;
import com.curiouskids.club.shared.Ids;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Writes audit entries with plain SQL: the log is append-only, so it needs no entity. */
@Service
class JdbcAuditLog implements AuditLog {

  private final JdbcClient jdbc;
  private final JsonMapper json;
  private final Ids ids;
  private final Clock clock;

  JdbcAuditLog(JdbcClient jdbc, JsonMapper json, Ids ids, Clock clock) {
    this.jdbc = jdbc;
    this.json = json;
    this.ids = ids;
    this.clock = clock;
  }

  @Override
  public void record(AuditEntry entry) {
    Timestamp now = Timestamp.from(clock.instant());
    jdbc.sql(
            """
            insert into audit_log
              (id, actor_account_id, action, entity_type, entity_id, details, at, created_at, updated_at)
            values
              (:id, :actor, :action, :entityType, :entityId, cast(:details as jsonb), :at, :at, :at)
            """)
        .param("id", ids.next())
        .param("actor", entry.actorAccountId())
        .param("action", entry.action())
        .param("entityType", entry.entityType())
        .param("entityId", entry.entityId())
        .param("details", json.writeValueAsString(entry.details()))
        .param("at", now)
        .update();
  }
}
