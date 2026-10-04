package com.curiouskids.club.notification.internal.email;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Email configuration under {@code club.notification.*}.
 *
 * @param from the sender, for example {@code Curiouskids Club <library@example.org>} ({@code
 *     MAIL_FROM})
 * @param appBaseUrl the web app's address, used to build links ({@code APP_BASE_URL})
 * @param transport {@code smtp} (Mailpit locally) or {@code ses} (staging and production)
 */
@Validated
@ConfigurationProperties("club.notification")
public record NotificationProperties(
    @NotBlank String from,
    @NotNull URI appBaseUrl,
    @NotNull @DefaultValue("smtp") Transport transport) {

  /** How emails leave the application. */
  public enum Transport {
    SMTP,
    SES
  }
}
