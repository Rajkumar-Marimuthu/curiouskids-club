package com.curiouskids.club.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.curiouskids.club.shared.AuditEntry;
import com.curiouskids.club.shared.AuditLog;
import com.curiouskids.club.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class AuditLogTest extends IntegrationTest {

  @Autowired AuditLog auditLog;
  @Autowired JdbcClient jdbc;

  @Test
  @DisplayName("FR-ADM-03: an audit entry is stored with actor, entity and JSON details")
  void recordsEntry() {
    UUID actor = UUID.randomUUID();
    UUID entity = UUID.randomUUID();

    auditLog.record(
        new AuditEntry(actor, "COPY_STATUS_CHANGED", "COPY", entity, Map.of("to", "LOST")));

    Map<String, Object> row =
        jdbc.sql(
                """
                select actor_account_id, action, entity_type, details->>'to' as to_status, at
                from audit_log where entity_id = :entity
                """)
            .param("entity", entity)
            .query()
            .singleRow();
    assertThat(row)
        .containsEntry("actor_account_id", actor)
        .containsEntry("action", "COPY_STATUS_CHANGED")
        .containsEntry("entity_type", "COPY")
        .containsEntry("to_status", "LOST");
    assertThat(row.get("at")).isNotNull();
  }

  @Test
  @DisplayName("FR-ADM-03: system actions have no actor and default to empty details")
  void systemEntry() {
    UUID entity = UUID.randomUUID();

    auditLog.record(new AuditEntry(null, "JOB_RAN", "JOB", entity, null));

    Map<String, Object> row =
        jdbc.sql(
                "select actor_account_id, details::text as details from audit_log where entity_id ="
                    + " :e")
            .param("e", entity)
            .query()
            .singleRow();
    assertThat(row.get("actor_account_id")).isNull();
    assertThat(row.get("details")).isEqualTo("{}");
  }
}
