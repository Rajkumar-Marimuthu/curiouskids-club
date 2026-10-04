package com.curiouskids.club.shared.internal.web;

import static com.curiouskids.club.support.Csrf.csrf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.curiouskids.club.support.IntegrationTest;
import com.curiouskids.testsupport.ProbeController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

@Import(ProbeController.class)
class ErrorHandlingTest extends IntegrationTest {

  private static final String TRACE_ID = "test-trace-0001";

  /** Error handling is the same for everyone; these checks run as a logged-in member. */
  @BeforeEach
  void asLoggedInMember() {
    mvc =
        mockMvc()
            .defaultRequest(get("/").with(user("member").roles("MEMBER")).with(csrf()))
            .build();
  }

  @Test
  @DisplayName("NFR-09: anonymous access to a protected or unknown route returns 401 problem+json")
  void anonymousGetsUnauthenticated() throws Exception {
    mockMvc()
        .build()
        .perform(get("/api/v1/no-such-thing").header(TraceIdFilter.HEADER, TRACE_ID))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.type").value("https://curiouskids.example/problems/unauthenticated"))
        .andExpect(jsonPath("$.traceId").value(TRACE_ID));
  }

  @Test
  @DisplayName("NFR-09: unknown route returns 404 problem+json with code and traceId")
  void unknownRoute() throws Exception {
    mvc.perform(get("/api/v1/no-such-thing").header(TraceIdFilter.HEADER, TRACE_ID))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(header().string(TraceIdFilter.HEADER, TRACE_ID))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.type").value("https://curiouskids.example/problems/not-found"))
        .andExpect(jsonPath("$.traceId").value(TRACE_ID));
  }

  @Test
  @DisplayName("NFR-09: invalid body returns 400 VALIDATION_FAILED with field errors")
  void invalidBody() throws Exception {
    mvc.perform(
            post("/test/probe")
                .header(TraceIdFilter.HEADER, TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.traceId").value(TRACE_ID))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  @DisplayName("NFR-09: invalid parameter returns 400 VALIDATION_FAILED with field errors")
  void invalidParameter() throws Exception {
    mvc.perform(get("/test/probe").param("size", "500"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("size"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  @Test
  @DisplayName("NFR-09: malformed JSON returns 400 VALIDATION_FAILED")
  void malformedJson() throws Exception {
    mvc.perform(post("/test/probe").contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  @DisplayName("NFR-09: a business error keeps its own status and code")
  void apiException() throws Exception {
    mvc.perform(get("/test/probe/slot-full"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SLOT_FULL"))
        .andExpect(jsonPath("$.title").value("Slot is full"))
        .andExpect(jsonPath("$.detail").value("The 17:00 window has no places left."));
  }

  @Test
  @DisplayName("NFR-09: unexpected errors return 500 without internals")
  void unexpectedError() throws Exception {
    mvc.perform(get("/test/probe/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(content().string(not(containsString("secret internals"))))
        .andExpect(content().string(not(containsString("IllegalStateException"))));
  }

  @Test
  @DisplayName("NFR-09: wrong method returns 405 METHOD_NOT_ALLOWED")
  void wrongMethod() throws Exception {
    mvc.perform(delete("/api/v1/ping"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  @DisplayName("NFR-09: a malformed incoming trace ID is replaced")
  void malformedTraceIdReplaced() throws Exception {
    mvc.perform(get("/api/v1/ping").header(TraceIdFilter.HEADER, "bad id\nwith newline"))
        .andExpect(status().isOk())
        .andExpect(header().string(TraceIdFilter.HEADER, not(containsString(" "))));
  }
}
