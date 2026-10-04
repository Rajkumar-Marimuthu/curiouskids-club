package com.curiouskids.club.notification.internal.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/** Picks the {@link EmailSender} adapter from {@code club.notification.transport}. */
@Configuration(proxyBeanMethods = false)
class EmailConfig {

  private static final String TRANSPORT = "club.notification.transport";

  @Bean
  @ConditionalOnProperty(name = TRANSPORT, havingValue = "smtp", matchIfMissing = true)
  EmailSender smtpEmailSender(JavaMailSender mail, NotificationProperties properties) {
    return new SmtpEmailSender(mail, properties.from());
  }

  @Bean
  @ConditionalOnProperty(name = TRANSPORT, havingValue = "ses")
  SesV2Client sesClient() {
    return SesV2Client.create();
  }

  @Bean
  @ConditionalOnProperty(name = TRANSPORT, havingValue = "ses")
  EmailSender sesEmailSender(SesV2Client ses, NotificationProperties properties) {
    return new SesEmailSender(ses, properties.from());
  }
}
