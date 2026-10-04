package com.curiouskids.club.shared;

import org.springframework.http.HttpStatus;

/**
 * Stable error codes returned in the {@code code} field of every problem+json response. The web app
 * switches on these; see {@code docs/architecture/api-conventions.md}.
 */
public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request is not valid"),
  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Not logged in"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "Not allowed"),
  NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
  NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Response format not available"),
  CONFLICT(HttpStatus.CONFLICT, "Conflict"),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
  EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Email not verified"),
  SLOT_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "A pickup slot is required"),
  SLOT_FULL(HttpStatus.CONFLICT, "Slot is full"),
  SLOT_NOT_BOOKABLE(HttpStatus.UNPROCESSABLE_CONTENT, "Slot cannot be booked"),
  LIMIT_REACHED(HttpStatus.UNPROCESSABLE_CONTENT, "Limit reached"),
  ALREADY_HOLDING_TITLE(HttpStatus.CONFLICT, "Already holding this title"),
  COPY_NOT_AVAILABLE(HttpStatus.CONFLICT, "Copy not available"),
  RENEWAL_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT, "Renewal not allowed"),
  IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT, "Idempotency key reused"),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");

  private final HttpStatus status;
  private final String title;

  ErrorCode(HttpStatus status, String title) {
    this.status = status;
    this.title = title;
  }

  public HttpStatus status() {
    return status;
  }

  public String title() {
    return title;
  }

  /** The generic code for a status raised by the framework rather than by our own code. */
  public static ErrorCode forStatus(int status) {
    return switch (status) {
      case 400 -> VALIDATION_FAILED;
      case 401 -> UNAUTHENTICATED;
      case 403 -> FORBIDDEN;
      case 404 -> NOT_FOUND;
      case 405 -> METHOD_NOT_ALLOWED;
      case 406 -> NOT_ACCEPTABLE;
      case 409 -> CONFLICT;
      case 415 -> UNSUPPORTED_MEDIA_TYPE;
      case 429 -> RATE_LIMITED;
      default -> status >= 500 ? INTERNAL_ERROR : VALIDATION_FAILED;
    };
  }
}
