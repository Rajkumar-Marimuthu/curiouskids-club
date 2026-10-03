package com.curiouskids.club.shared;

/**
 * Thrown by module services to end a request with a problem+json response carrying a stable {@link
 * ErrorCode}. The message becomes the {@code detail}, so it must not contain personal data.
 */
public class ApiException extends RuntimeException {

  private final ErrorCode code;

  public ApiException(ErrorCode code, String detail) {
    super(detail);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }
}
