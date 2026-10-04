package com.curiouskids.club.support;

import com.curiouskids.club.notification.internal.email.EmailSender;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Wraps the real SMTP sender so tests can make the email provider fail on demand. */
@TestConfiguration(proxyBeanMethods = false)
class TestEmailConfig {

  @Bean
  @Primary
  FaultInjectingEmailSender faultInjectingEmailSender(
      @Qualifier("smtpEmailSender") EmailSender smtp) {
    return new FaultInjectingEmailSender(smtp);
  }
}
