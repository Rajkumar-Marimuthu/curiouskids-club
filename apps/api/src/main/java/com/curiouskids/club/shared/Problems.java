package com.curiouskids.club.shared;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds RFC 9457 problem+json bodies with a stable {@code code} and the request's {@code traceId}.
 * Used by the exception handler and by servlet filters (security) that run before it.
 */
@Component
public class Problems {

  /** MDC key the trace ID filter sets for every request. */
  public static final String TRACE_ID_MDC_KEY = "traceId";

  private final ClubProperties properties;
  private final JsonMapper json;

  public Problems(ClubProperties properties, JsonMapper json) {
    this.properties = properties;
    this.json = json;
  }

  /** Fills in type, title, code and traceId on a problem. */
  public ProblemDetail decorate(ProblemDetail body, ErrorCode code) {
    body.setType(
        properties
            .problemTypeBase()
            .resolve(code.name().toLowerCase(Locale.ROOT).replace('_', '-')));
    body.setTitle(code.title());
    body.setProperty("code", code.name());
    body.setProperty("traceId", MDC.get(TRACE_ID_MDC_KEY));
    return body;
  }

  /** Writes a problem directly to the response, for code that runs outside Spring MVC. */
  public void write(HttpServletResponse response, ErrorCode code, String detail)
      throws IOException {
    ProblemDetail body = decorate(ProblemDetail.forStatusAndDetail(code.status(), detail), code);
    body.setInstance(null);
    response.setStatus(code.status().value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    json.writeValue(response.getOutputStream(), body);
  }
}
