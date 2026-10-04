package com.curiouskids.club.notification.internal.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.curiouskids.club.notification.EmailType;
import com.curiouskids.club.notification.internal.email.NotificationProperties.Transport;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EmailTemplatesTest {

  private final EmailTemplates templates =
      new EmailTemplates(
          new NotificationProperties(
              "Club <club@example.org>", URI.create("https://club.example/"), Transport.SMTP));

  @ParameterizedTest
  @EnumSource(EmailType.class)
  @DisplayName("FR-NOT-01: every email type renders a mobile-friendly HTML body and a text body")
  void everyTypeRendersBothParts(EmailType type) {
    RenderedEmail email = templates.render(type, "a@example.com", Map.of("path", "/go?t=1"));

    assertThat(email.to()).isEqualTo("a@example.com");
    assertThat(email.subject()).isNotBlank();
    assertThat(email.html())
        .contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        .contains("max-width:560px")
        .contains("href=\"https://club.example/go?t=1\"")
        .contains("<title>" + email.subject().replace("'", "&#39;") + "</title>")
        .doesNotContain(" th:")
        .doesNotContain("xmlns:th")
        .doesNotContain("${");
    assertThat(email.text())
        .startsWith("Curiouskids Club")
        .contains("https://club.example/go?t=1")
        .doesNotContain("${")
        .doesNotContain("<");
  }

  @Test
  @DisplayName("FR-NOT-01: values are escaped in the HTML body")
  void valuesAreEscaped() {
    RenderedEmail email =
        templates.render(
            EmailType.VERIFY_EMAIL, "a@example.com", Map.of("path", "/v?a=1&b=<script>"));

    assertThat(email.html()).doesNotContain("<script>").contains("&lt;script&gt;");
  }
}
