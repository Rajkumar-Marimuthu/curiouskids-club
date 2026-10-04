package com.curiouskids.club.support;

import com.curiouskids.club.notification.internal.email.EmailSender;
import com.curiouskids.club.notification.internal.email.RenderedEmail;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.mail.MailSendException;

/**
 * The real SMTP sender, plus a switch that makes the provider fail like an outage would. Counts
 * every send attempt that reached the provider.
 */
public class FaultInjectingEmailSender implements EmailSender {

  private final EmailSender delegate;
  private final AtomicBoolean failing = new AtomicBoolean();
  private final AtomicInteger attempts = new AtomicInteger();

  FaultInjectingEmailSender(EmailSender delegate) {
    this.delegate = delegate;
  }

  @Override
  public void send(RenderedEmail email) {
    attempts.incrementAndGet();
    if (failing.get()) {
      // Real provider errors often include the address; the outbox must not log it.
      throw new MailSendException("Simulated outage sending to " + email.to());
    }
    delegate.send(email);
  }

  public void failing(boolean failing) {
    this.failing.set(failing);
  }

  public int attempts() {
    return attempts.get();
  }

  public void reset() {
    failing.set(false);
    attempts.set(0);
  }
}
