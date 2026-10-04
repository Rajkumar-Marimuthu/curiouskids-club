package com.curiouskids.club.identity.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * T-014: the first admin is invited at start-up from {@code club.identity.bootstrap-admin-email}
 * (environment {@code CLUB_BOOTSTRAP_ADMIN_EMAIL}), so no password is ever configured. Nothing is
 * sent once an admin exists, or while an earlier invitation still works.
 */
@Component
class FirstAdminInvitation {

  private static final Logger log = LoggerFactory.getLogger(FirstAdminInvitation.class);

  private final IdentityProperties properties;
  private final StaffInvitationService invitations;

  FirstAdminInvitation(IdentityProperties properties, StaffInvitationService invitations) {
    this.properties = properties;
    this.invitations = invitations;
  }

  @EventListener(ApplicationReadyEvent.class)
  void onStart() {
    String email = properties.bootstrapAdminEmail();
    if (email == null || email.isBlank()) {
      return;
    }
    if (invitations.inviteFirstAdmin(email)) {
      log.info("First admin invited");
    } else {
      log.info("First admin invitation not needed");
    }
  }
}
