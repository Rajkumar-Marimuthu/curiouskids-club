package com.curiouskids.club.notification.internal.outbox;

import org.springframework.context.ApplicationContext;

/** Lets tests in other modules run the outbox dispatcher, as the scheduled job would. */
public final class OutboxTestSupport {

  /** Sends every due outbox email now; returns how many were sent. */
  public static int dispatch(ApplicationContext context) {
    return context.getBean(OutboxDispatcher.class).dispatchDue();
  }

  private OutboxTestSupport() {}
}
