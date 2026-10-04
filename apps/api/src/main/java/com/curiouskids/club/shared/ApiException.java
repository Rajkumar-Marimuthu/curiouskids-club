package com.curiouskids.club.shared;

import java.util.List;

/**
 * Thrown by module services to end a request with a problem+json response carrying a stable {@link
 * ErrorCode}. The message becomes the {@code detail}, so it must not contain personal data.
 */
public class ApiException extends RuntimeException {

  private final ErrorCode code;
  private final List<FieldError> fieldErrors;

  public ApiException(ErrorCode code, String detail) {
    this(code, detail, List.of());
  }

  public ApiException(ErrorCode code, String detail, List<FieldError> fieldErrors) {
    super(detail);
    this.code = code;
    this.fieldErrors = List.copyOf(fieldErrors);
  }

  public ErrorCode code() {
    return code;
  }

  /** Rendered as the problem's {@code errors}, like Bean Validation failures. */
  public List<FieldError> fieldErrors() {
    return fieldErrors;
  }

  /** A rule a request field broke; the message is shown next to the field. */
  public record FieldError(String field, String message) {}
}
