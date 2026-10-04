package com.curiouskids.club.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OutboxEmailTest {

  @Test
  @DisplayName("FR-NOT-01: an email carries exactly the payload values its templates use")
  void payloadMustMatchType() {
    assertThatThrownBy(() -> email(Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("[path]");
    assertThatThrownBy(() -> email(Map.of("path", "/a", "name", "Sam")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://evil.example/x", "//evil.example/x", "verify"})
  @DisplayName("FR-NOT-01: links only point into the web app")
  void pathMustBeInTheWebApp(String path) {
    assertThatThrownBy(() -> email(Map.of("path", path)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("DB-09: a dedupe key is required and fits the column")
  void dedupeKeyRequired() {
    assertThatThrownBy(
            () ->
                new OutboxEmail(
                    EmailType.VERIFY_EMAIL, null, "a@example.com", Map.of("path", "/v"), " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new OutboxEmail(
                    EmailType.VERIFY_EMAIL,
                    null,
                    "a@example.com",
                    Map.of("path", "/v"),
                    "k".repeat(201)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("FR-NOT-03: the payload is copied, so later changes do not alter the queued email")
  void payloadIsCopied() {
    Map<String, String> payload = new HashMap<>(Map.of("path", "/v"));
    OutboxEmail email = email(payload);
    payload.put("path", "/changed");

    assertThat(email.payload()).containsEntry("path", "/v");
  }

  private static OutboxEmail email(Map<String, String> payload) {
    return new OutboxEmail(EmailType.VERIFY_EMAIL, null, "a@example.com", payload, "key");
  }
}
