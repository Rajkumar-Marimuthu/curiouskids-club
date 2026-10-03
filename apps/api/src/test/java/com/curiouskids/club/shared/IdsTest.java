package com.curiouskids.club.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdsTest {

  private static final Instant NOW = Instant.parse("2026-09-21T16:00:00.123Z");

  @Test
  @DisplayName("NFR-03: IDs are UUID v7 carrying the clock's millisecond timestamp")
  void generatesVersion7() {
    UUID id = new Ids(Clock.fixed(NOW, ZoneOffset.UTC)).next();

    assertThat(id.version()).isEqualTo(7);
    assertThat(id.variant()).isEqualTo(2);
    assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(NOW.toEpochMilli());
  }

  @Test
  @DisplayName("NFR-03: IDs are unique and sort by creation time")
  void uniqueAndTimeOrdered() {
    Ids earlier = new Ids(Clock.fixed(NOW, ZoneOffset.UTC));
    Ids later = new Ids(Clock.fixed(NOW.plusMillis(1), ZoneOffset.UTC));

    Set<UUID> seen = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      seen.add(earlier.next());
    }
    assertThat(seen).hasSize(1000);
    assertThat(earlier.next().toString()).isLessThan(later.next().toString());
  }
}
