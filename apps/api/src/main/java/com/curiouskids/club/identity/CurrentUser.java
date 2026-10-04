package com.curiouskids.club.identity;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/**
 * The logged-in account, as stored in the session. Inject it in controllers with
 * {@code @AuthenticationPrincipal CurrentUser user}; derive ownership from it, never from IDs the
 * client sends (security-and-privacy.md). Holds no personal data.
 *
 * @param accountId the account
 * @param role {@code MEMBER}, {@code VOLUNTEER} or {@code ADMIN} (BR-31)
 * @param familyId the member's family, or null for staff
 */
public record CurrentUser(UUID accountId, String role, UUID familyId)
    implements Principal, Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** Volunteers and admins have shorter sessions (security-and-privacy.md). */
  public boolean isStaff() {
    return !"MEMBER".equals(role);
  }

  /** The account ID; also the session's principal name, so sessions can be found per account. */
  @Override
  public String getName() {
    return accountId.toString();
  }
}
