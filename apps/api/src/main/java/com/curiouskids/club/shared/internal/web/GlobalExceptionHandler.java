package com.curiouskids.club.shared.internal.web;

import com.curiouskids.club.shared.ApiException;
import com.curiouskids.club.shared.ErrorCode;
import com.curiouskids.club.shared.Problems;
import com.curiouskids.club.shared.RateLimitedException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Turns every error into RFC 9457 problem+json with a stable {@code code} and the request's {@code
 * traceId}. Never exposes stack traces, SQL or personal data.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final Problems problems;

  GlobalExceptionHandler(Problems problems) {
    this.problems = problems;
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(ex.code().status(), ex.getMessage());
    if (!ex.fieldErrors().isEmpty()) {
      body.setProperty(
          "errors",
          ex.fieldErrors().stream()
              .map(e -> Map.of("field", e.field(), "message", e.message()))
              .toList());
    }
    HttpHeaders headers = new HttpHeaders();
    if (ex instanceof RateLimitedException limited) {
      headers.set(HttpHeaders.RETRY_AFTER, Long.toString(limited.retryAfter().toSeconds()));
    }
    return toResponse(body, ex.code(), headers);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
    log.error("Unhandled exception", ex);
    ErrorCode code = ErrorCode.INTERNAL_ERROR;
    ProblemDetail body =
        ProblemDetail.forStatusAndDetail(code.status(), "An unexpected error occurred.");
    return toResponse(body, code, new HttpHeaders());
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail body = ex.getBody();
    body.setProperty(
        "errors",
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> fieldError(e.getField(), e))
            .toList());
    return toResponse(body, ErrorCode.VALIDATION_FAILED, headers);
  }

  /** An unknown field gets a field error naming it, so callers see what was refused. */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    if (ex.getCause() instanceof UnrecognizedPropertyException unknown) {
      ProblemDetail body =
          ProblemDetail.forStatusAndDetail(
              ErrorCode.VALIDATION_FAILED.status(),
              "The request has a field that is not accepted.");
      body.setProperty(
          "errors",
          List.of(Map.of("field", unknown.getPropertyName(), "message", "is not accepted")));
      return toResponse(body, ErrorCode.VALIDATION_FAILED, headers);
    }
    return super.handleHttpMessageNotReadable(ex, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail body = ex.getBody();
    body.setProperty(
        "errors",
        ex.getParameterValidationResults().stream()
            .flatMap(
                r ->
                    r.getResolvableErrors().stream()
                        .map(e -> fieldError(r.getMethodParameter().getParameterName(), e)))
            .toList());
    return toResponse(body, ErrorCode.VALIDATION_FAILED, headers);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail problem =
        body instanceof ProblemDetail p ? p : ProblemDetail.forStatus(status.value());
    return toResponse(problem, ErrorCode.forStatus(status.value()), headers);
  }

  private ResponseEntity<Object> toResponse(
      ProblemDetail body, ErrorCode code, HttpHeaders headers) {
    problems.decorate(body, code);
    return ResponseEntity.status(body.getStatus()).headers(headers).body(body);
  }

  private static Map<String, String> fieldError(String field, MessageSourceResolvable error) {
    String message = error.getDefaultMessage() == null ? "is not valid" : error.getDefaultMessage();
    return Map.of("field", field == null ? "" : field, "message", message);
  }
}
