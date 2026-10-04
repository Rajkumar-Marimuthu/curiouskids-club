package com.curiouskids.club.identity.internal;

import com.curiouskids.club.shared.Ids;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * FR-ID-03: after 5 failed logins in 15 minutes for one email, or 20 from one address, further
 * attempts are refused until 15 minutes after the failure that reached the limit. Counted in the
 * database so the limit holds across instances. Counted per email rather than per account, so
 * unknown emails behave the same as known ones.
 */
@Component
class LoginThrottle {

  static final Duration WINDOW = Duration.ofMinutes(15);
  static final int MAX_PER_EMAIL = 5;
  static final int MAX_PER_ADDRESS = 20;
  static final Duration KEEP = Duration.ofDays(1);

  private final JdbcClient jdbc;
  private final Ids ids;
  private final Clock clock;

  LoginThrottle(JdbcClient jdbc, Ids ids, Clock clock) {
    this.jdbc = jdbc;
    this.ids = ids;
    this.clock = clock;
  }

  /** How long the caller must wait, or empty if they may try now. */
  Optional<Duration> blockedFor(String emailHash, String ipHash) {
    Instant now = clock.instant();
    Optional<Instant> byEmail = limitReachedAt("email_hash", emailHash, MAX_PER_EMAIL, now);
    Optional<Instant> byAddress = limitReachedAt("ip_hash", ipHash, MAX_PER_ADDRESS, now);
    return Stream.of(byEmail, byAddress)
        .flatMap(Optional::stream)
        .max(Comparator.naturalOrder())
        .map(reached -> Duration.between(now, reached.plus(WINDOW)));
  }

  void recordFailure(String emailHash, String ipHash) {
    Timestamp now = Timestamp.from(clock.instant());
    jdbc.sql(
            """
            insert into login_failure (id, email_hash, ip_hash, at, created_at, updated_at)
            values (:id, :emailHash, :ipHash, :now, :now, :now)
            """)
        .param("id", ids.next())
        .param("emailHash", emailHash)
        .param("ipHash", ipHash)
        .param("now", now)
        .update();
  }

  /** A successful login forgets that email's failures. */
  void clear(String emailHash) {
    jdbc.sql("delete from login_failure where email_hash = :emailHash")
        .param("emailHash", emailHash)
        .update();
  }

  /** Deletes failures older than a day; returns how many. */
  int purge() {
    return jdbc.sql("delete from login_failure where at < :cutoff")
        .param("cutoff", Timestamp.from(clock.instant().minus(KEEP)))
        .update();
  }

  /** When the limit-th most recent failure in the window happened, if there were that many. */
  private Optional<Instant> limitReachedAt(String column, String hash, int limit, Instant now) {
    return jdbc.sql(
            "select at from login_failure where "
                + column
                + " = :hash and at > :since order by at desc offset :skip limit 1")
        .param("hash", hash)
        .param("since", Timestamp.from(now.minus(WINDOW)))
        .param("skip", limit - 1)
        .query(Timestamp.class)
        .optional()
        .map(Timestamp::toInstant);
  }
}
