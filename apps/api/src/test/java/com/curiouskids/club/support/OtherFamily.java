package com.curiouskids.club.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Access-control harness (security-and-privacy.md): requests aimed at one family's resource, sent
 * as a member of another family, must all get 404 NOT_FOUND, exactly as if the resource did not
 * exist. Use it for every endpoint that takes a family-owned ID.
 */
public final class OtherFamily {

  private OtherFamily() {}

  /**
   * Sends each request as {@code intruder} and checks it gets the same 404 problem as a random ID.
   *
   * @param requests requests naming the owner's resource; CSRF is added here
   */
  public static void cannotReach(
      MockMvc mvc, TestAccounts.Account intruder, List<MockHttpServletRequestBuilder> requests)
      throws Exception {
    for (MockHttpServletRequestBuilder request : requests) {
      var response =
          mvc.perform(request.with(intruder.session()).with(Csrf.csrf())).andReturn().getResponse();
      assertThat(response.getStatus()).as("status for another family").isEqualTo(404);
      assertThat(response.getContentAsString()).contains("\"code\":\"NOT_FOUND\"");
    }
  }
}
