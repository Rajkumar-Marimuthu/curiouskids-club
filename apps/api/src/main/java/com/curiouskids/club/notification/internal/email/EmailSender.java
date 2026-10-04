package com.curiouskids.club.notification.internal.email;

/**
 * Port to the email provider: SMTP (Mailpit) locally, Amazon SES in AWS. Selected by {@code
 * club.notification.transport}.
 */
public interface EmailSender {

  /** Sends the email or throws; the outbox retries on any exception. */
  void send(RenderedEmail email);
}
