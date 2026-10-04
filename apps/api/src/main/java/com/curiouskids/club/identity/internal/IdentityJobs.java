package com.curiouskids.club.identity.internal;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily clean-up of failed-login and request-limit records (kept one day) and of closed or expired
 * staff invitations (kept 30 days).
 */
@Component
@ConditionalOnProperty(name = "club.jobs.enabled", havingValue = "true", matchIfMissing = true)
class IdentityJobs {

  private final LoginThrottle loginThrottle;
  private final RequestThrottle requestThrottle;
  private final StaffInvitationService invitations;

  IdentityJobs(
      LoginThrottle loginThrottle,
      RequestThrottle requestThrottle,
      StaffInvitationService invitations) {
    this.loginThrottle = loginThrottle;
    this.requestThrottle = requestThrottle;
    this.invitations = invitations;
  }

  @Scheduled(cron = "0 15 3 * * *", zone = "${club.timezone}")
  @SchedulerLock(name = "identity-login-failure-purge", lockAtMostFor = "PT10M")
  void purgeLoginFailures() {
    loginThrottle.purge();
  }

  @Scheduled(cron = "0 20 3 * * *", zone = "${club.timezone}")
  @SchedulerLock(name = "identity-request-throttle-purge", lockAtMostFor = "PT10M")
  void purgeRequestThrottle() {
    requestThrottle.purge();
  }

  @Scheduled(cron = "0 25 3 * * *", zone = "${club.timezone}")
  @SchedulerLock(name = "identity-staff-invitation-purge", lockAtMostFor = "PT10M")
  void purgeStaffInvitations() {
    invitations.purge();
  }
}
