package com.curiouskids.club;

import static org.assertj.core.api.Assertions.assertThat;

import com.curiouskids.club.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class ClubApplicationTests extends IntegrationTest {

  @Autowired JdbcClient jdbc;

  @Test
  @DisplayName("NFR-11: baseline migration applies on an empty PostgreSQL")
  void migrationApplies() {
    assertThat(
            jdbc.sql(
                    """
                    select table_name from information_schema.tables
                    where table_schema = 'public'
                      and table_name in ('setting', 'audit_log', 'shedlock')
                    """)
                .query(String.class)
                .list())
        .containsExactlyInAnyOrder("setting", "audit_log", "shedlock");
  }
}
