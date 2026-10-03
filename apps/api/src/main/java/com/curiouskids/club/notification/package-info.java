/**
 * Outbox, templates, email delivery and scheduled reminders. Reacts to events; nothing depends on
 * it.
 */
@ApplicationModule(allowedDependencies = {"circulation", "identity", "scheduling", "shared"})
package com.curiouskids.club.notification;

import org.springframework.modulith.ApplicationModule;
