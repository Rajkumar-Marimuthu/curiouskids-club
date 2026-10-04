package com.curiouskids.club.notification.internal.outbox;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled outbox jobs. They run in every instance; ShedLock lets only one run at a time. Off when
 * {@code club.jobs.enabled=false} (tests drive the jobs directly).
 */
@Component
@ConditionalOnProperty(name = "club.jobs.enabled", havingValue = "true", matchIfMissing = true)
class OutboxJobs {

  private final OutboxDispatcher dispatcher;
  private final OutboxRetention retention;

  OutboxJobs(OutboxDispatcher dispatcher, OutboxRetention retention) {
    this.dispatcher = dispatcher;
    this.retention = retention;
  }

  @Scheduled(fixedDelayString = "${club.notification.poll-interval:PT30S}")
  @SchedulerLock(name = "notification-outbox-dispatch", lockAtMostFor = "PT10M")
  void dispatch() {
    dispatcher.dispatchDue();
  }

  @Scheduled(cron = "0 30 3 * * *", zone = "${club.timezone}")
  @SchedulerLock(name = "notification-outbox-retention", lockAtMostFor = "PT10M")
  void retention() {
    retention.clearSentDetails();
  }
}
