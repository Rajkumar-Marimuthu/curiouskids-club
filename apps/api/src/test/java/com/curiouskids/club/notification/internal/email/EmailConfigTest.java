package com.curiouskids.club.notification.internal.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class EmailConfigTest {

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(NotificationProperties.class)
  static class Properties {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(Properties.class, EmailConfig.class)
          .withBean(JavaMailSenderImpl.class)
          .withPropertyValues(
              "club.notification.from=club@example.org",
              "club.notification.app-base-url=https://club.example");

  @Test
  @DisplayName("FR-NOT-01: SMTP is the default transport (Mailpit locally)")
  void smtpByDefault() {
    runner.run(
        context ->
            assertThat(context.getBean(EmailSender.class)).isInstanceOf(SmtpEmailSender.class));
  }

  @Test
  @DisplayName("FR-NOT-01: staging and production send through SES")
  void sesWhenConfigured() {
    runner
        .withPropertyValues("club.notification.transport=ses")
        .withSystemProperties("aws.region=eu-west-2")
        .run(
            context ->
                assertThat(context.getBean(EmailSender.class)).isInstanceOf(SesEmailSender.class));
  }
}
