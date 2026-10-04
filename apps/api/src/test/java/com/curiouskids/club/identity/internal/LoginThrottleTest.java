package com.curiouskids.club.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.curiouskids.club.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class LoginThrottleTest extends IntegrationTest {

  @Autowired LoginThrottle throttle;
  @Autowired JdbcClient jdbc;

  @BeforeEach
  void clean() {
    jdbc.sql("delete from login_failure").update();
  }

  @Test
  @DisplayName("FR-ID-03: failed-login records older than a day are purged")
  void purgesRecordsOlderThanADay() {
    throttle.recordFailure("a".repeat(64), "b".repeat(64));
    throttle.recordFailure("c".repeat(64), "b".repeat(64));
    jdbc.sql("update login_failure set at = at - interval '25 hours' where email_hash like 'a%'")
        .update();

    assertThat(throttle.purge()).isEqualTo(1);
    assertThat(jdbc.sql("select email_hash from login_failure").query(String.class).list())
        .containsExactly("c".repeat(64));
  }

  @Test
  @DisplayName("FR-ID-03: failures from other emails and addresses do not count")
  void countsPerEmailAndPerAddress() {
    for (int i = 0; i < 4; i++) {
      throttle.recordFailure("a".repeat(64), "b".repeat(64));
    }
    throttle.recordFailure("a".repeat(64), "d".repeat(64));

    assertThat(throttle.blockedFor("a".repeat(64), "e".repeat(64))).isPresent();
    assertThat(throttle.blockedFor("c".repeat(64), "b".repeat(64))).isEmpty();
  }
}
