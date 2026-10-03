package com.curiouskids.club.shared.internal;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The only place that reads the system clock; everything else injects {@link Clock}. */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
