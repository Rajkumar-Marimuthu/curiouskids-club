package com.curiouskids.club.identity.internal;

import com.curiouskids.club.shared.Ids;
import com.curiouskids.club.shared.RateLimitedException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Hourly limits on registration and emailed links (FR-ID-04, security-and-privacy.md): register 10
 * per address; reset and verification emails together 3 per email and 20 per address (see {@link
 * IdentityProperties.RequestLimits}). Every request counts, whether or not the email is known, so a
 * 429 reveals nothing. Counted in the database so the limits hold across instances; two requests at
 * the same moment may both pass, which only matters at the edge of a limit.
 */
@Component
class RequestThrottle {

  static final Duration WINDOW = Duration.ofHours(1);
  static final Duration KEEP = Duration.ofDays(1);

  private final IdentityProperties.RequestLimits limits;
  private final JdbcClient jdbc;
  private final Ids ids;
  private final Clock clock;

  RequestThrottle(IdentityProperties properties, JdbcClient jdbc, Ids ids, Clock clock) {
    this.limits = properties.requestLimits();
    this.jdbc = jdbc;
    this.ids = ids;
    this.clock = clock;
  }

  /**
   * Counts a registration from this address.
   *
   * @throws RateLimitedException if the address already used its hourly allowance
   */
  void register(String clientAddress) {
    String address = "ip:" + Tokens.hash(clientAddress);
    check("REGISTER", address, limits.registerPerAddress());
    record("REGISTER", address);
  }

  /**
   * Counts a request for a reset or verification email.
   *
   * @param email the normalised email
   * @throws RateLimitedException if the email or the address already used its hourly allowance
   */
  void emailLink(String email, String clientAddress) {
    String byEmail = "email:" + Tokens.hash(email);
    String byAddress = "ip:" + Tokens.hash(clientAddress);
    check("EMAIL_LINK", byEmail, limits.emailLinksPerEmail());
    check("EMAIL_LINK", byAddress, limits.emailLinksPerAddress());
    record("EMAIL_LINK", byEmail);
    record("EMAIL_LINK", byAddress);
  }

  /** Deletes requests older than a day; returns how many. */
  int purge() {
    return jdbc.sql("delete from request_throttle where at < :cutoff")
        .param("cutoff", Timestamp.from(clock.instant().minus(KEEP)))
        .update();
  }

  private void check(String action, String keyHash, int limit) {
    Instant now = clock.instant();
    // The oldest of the last `limit` requests in the window frees up one hour after it was made.
    Optional<Instant> oldestCounted =
        jdbc.sql(
                """
                select at from request_throttle
                where action = :action and key_hash = :key and at > :since
                order by at desc offset :skip limit 1
                """)
            .param("action", action)
            .param("key", keyHash)
            .param("since", Timestamp.from(now.minus(WINDOW)))
            .param("skip", limit - 1)
            .query(Timestamp.class)
            .optional()
            .map(Timestamp::toInstant);
    if (oldestCounted.isPresent()) {
      throw new RateLimitedException(
          "Too many requests. Please try again later.",
          Duration.between(now, oldestCounted.get().plus(WINDOW)));
    }
  }

  private void record(String action, String keyHash) {
    Timestamp now = Timestamp.from(clock.instant());
    jdbc.sql(
            """
            insert into request_throttle (id, action, key_hash, at, created_at, updated_at)
            values (:id, :action, :key, :now, :now, :now)
            """)
        .param("id", ids.next())
        .param("action", action)
        .param("key", keyHash)
        .param("now", now)
        .update();
  }
}
