package com.curiouskids.club.notification.internal.identity;

import com.curiouskids.club.identity.EmailChangeRequested;
import com.curiouskids.club.identity.EmailChanged;
import com.curiouskids.club.identity.EmailVerificationRequested;
import com.curiouskids.club.identity.PasswordResetRequested;
import com.curiouskids.club.notification.EmailType;
import com.curiouskids.club.notification.Notifications;
import com.curiouskids.club.notification.OutboxEmail;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Queues the emails identity asks for. A plain (synchronous) listener runs inside the publishing
 * transaction, so the outbox row commits or rolls back with the business change (FR-NOT-03).
 */
@Component
class IdentityEmails {

  private final Notifications notifications;

  IdentityEmails(Notifications notifications) {
    this.notifications = notifications;
  }

  @EventListener
  void on(EmailVerificationRequested event) {
    notifications.enqueue(
        new OutboxEmail(
            EmailType.VERIFY_EMAIL,
            event.accountId(),
            event.email(),
            Map.of("path", "/verify-email?token=" + event.token()),
            "VERIFY_EMAIL:" + event.tokenId()));
  }

  @EventListener
  void on(PasswordResetRequested event) {
    notifications.enqueue(
        new OutboxEmail(
            EmailType.PASSWORD_RESET,
            event.accountId(),
            event.email(),
            Map.of("path", "/reset-password?token=" + event.token()),
            "PASSWORD_RESET:" + event.tokenId()));
  }

  @EventListener
  void on(EmailChangeRequested event) {
    notifications.enqueue(
        new OutboxEmail(
            EmailType.EMAIL_CHANGE,
            event.accountId(),
            event.newEmail(),
            Map.of("path", "/confirm-email?token=" + event.token()),
            "EMAIL_CHANGE:" + event.tokenId()));
  }

  @EventListener
  void on(EmailChanged event) {
    notifications.enqueue(
        new OutboxEmail(
            EmailType.EMAIL_CHANGED,
            event.accountId(),
            event.previousEmail(),
            Map.of(),
            "EMAIL_CHANGED:" + event.tokenId()));
  }
}
