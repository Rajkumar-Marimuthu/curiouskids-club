package com.curiouskids.club.shared;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Generates time-ordered UUID v7 identifiers (RFC 9562) from the injected clock. */
@Component
public class Ids {

  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public Ids(Clock clock) {
    this.clock = clock;
  }

  public UUID next() {
    long millis = clock.millis();
    byte[] rand = new byte[10];
    random.nextBytes(rand);

    // 48-bit Unix millis, 4-bit version (7), 12 random bits.
    long msb = (millis & 0xFFFF_FFFF_FFFFL) << 16;
    msb |= 0x7000L;
    msb |= ((rand[0] & 0x0FL) << 8) | (rand[1] & 0xFFL);

    // 2-bit variant (10), 62 random bits.
    long lsb = 0;
    for (int i = 2; i < 10; i++) {
      lsb = (lsb << 8) | (rand[i] & 0xFFL);
    }
    lsb = (lsb & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;

    return new UUID(msb, lsb);
  }
}
