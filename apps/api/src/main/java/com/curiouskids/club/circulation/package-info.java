/** Reservations, waitlist, promotion, holds, expiry, loans, renewals and overdue. */
@ApplicationModule(allowedDependencies = {"catalogue", "scheduling", "identity", "shared"})
package com.curiouskids.club.circulation;

import org.springframework.modulith.ApplicationModule;
