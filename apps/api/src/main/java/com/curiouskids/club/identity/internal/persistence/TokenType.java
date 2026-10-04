package com.curiouskids.club.identity.internal.persistence;

import java.time.Duration;

/** Single-use emailed tokens and how long each lives (security-and-privacy.md). */
public enum TokenType {
  EMAIL_VERIFY(Duration.ofHours(24)),
  PASSWORD_RESET(Duration.ofHours(1)),
  STAFF_INVITE(Duration.ofDays(7));

  private final Duration lifetime;

  TokenType(Duration lifetime) {
    this.lifetime = lifetime;
  }

  public Duration lifetime() {
    return lifetime;
  }
}
