package com.curiouskids.club.shared;

import java.time.Duration;

/** Ends a request with 429 {@code RATE_LIMITED} and a {@code Retry-After} header (seconds). */
public class RateLimitedException extends ApiException {

  private final Duration retryAfter;

  public RateLimitedException(String detail, Duration retryAfter) {
    super(ErrorCode.RATE_LIMITED, detail);
    this.retryAfter = retryAfter;
  }

  public Duration retryAfter() {
    return retryAfter;
  }
}
