package com.curiouskids.club.identity.internal;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily clean-up of failed-login records (kept one day). */
@Component
@ConditionalOnProperty(name = "club.jobs.enabled", havingValue = "true", matchIfMissing = true)
class IdentityJobs {

  private final LoginThrottle throttle;

  IdentityJobs(LoginThrottle throttle) {
    this.throttle = throttle;
  }

  @Scheduled(cron = "0 15 3 * * *", zone = "${club.timezone}")
  @SchedulerLock(name = "identity-login-failure-purge", lockAtMostFor = "PT10M")
  void purgeLoginFailures() {
    throttle.purge();
  }
}
