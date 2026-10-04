package com.curiouskids.club.identity.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.curiouskids.club.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * ADR-0004 harness: every state-changing endpoint of the API, now and later, refuses a request
 * without the CSRF token, even from a logged-in user. New endpoints are picked up automatically.
 */
class CsrfHarnessTest extends IntegrationTest {

  private static final Set<RequestMethod> STATE_CHANGING =
      Set.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  @Test
  @DisplayName("ADR-0004: every POST, PUT, PATCH and DELETE endpoint requires the CSRF token")
  void everyStateChangingEndpointNeedsCsrf() throws Exception {
    List<String> checked = new ArrayList<>();
    for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
      for (RequestMethod method : info.getMethodsCondition().getMethods()) {
        if (!STATE_CHANGING.contains(method)) {
          continue;
        }
        for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
          String path = pattern.replaceAll("\\{[^}]+}", "00000000-0000-0000-0000-000000000000");
          int status =
              mvc.perform(
                      request(HttpMethod.valueOf(method.name()), path)
                          .with(user("member").roles("MEMBER", "VOLUNTEER", "ADMIN"))
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{}"))
                  .andReturn()
                  .getResponse()
                  .getStatus();
          assertThat(status).as("%s %s without CSRF token", method, path).isEqualTo(403);
          checked.add(method + " " + path);
        }
      }
    }
    assertThat(checked)
        .contains(
            "POST /api/v1/auth/register",
            "POST /api/v1/auth/login",
            "POST /api/v1/auth/logout",
            "POST /api/v1/auth/verify-email",
            "POST /api/v1/auth/verify-email/resend");
  }
}
