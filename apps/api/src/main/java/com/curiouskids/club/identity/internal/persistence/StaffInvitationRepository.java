package com.curiouskids.club.identity.internal.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface StaffInvitationRepository extends JpaRepository<StaffInvitationEntity, UUID> {

  /** Locks the invitation so two requests with the same link cannot both use it. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<StaffInvitationEntity> findByTokenHash(String tokenHash);

  /** Whether an open invitation for the (normalised) email is still within its lifetime. */
  @Query(
      "select count(i) > 0 from StaffInvitationEntity i where i.email = :email"
          + " and i.acceptedAt is null and i.revokedAt is null and i.expiresAt > :now")
  boolean existsUsable(String email, Instant now);

  /** Revokes the open invitation for the (normalised) email, if any, so its link stops working. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update StaffInvitationEntity i set i.revokedAt = :now, i.updatedAt = :now"
          + " where i.email = :email and i.acceptedAt is null and i.revokedAt is null")
  int revokeOpen(String email, Instant now);

  /** Deletes invitations closed or expired before {@code cutoff}; their emails are not kept. */
  @Modifying
  @Query(
      "delete from StaffInvitationEntity i where i.acceptedAt < :cutoff or i.revokedAt < :cutoff"
          + " or (i.acceptedAt is null and i.expiresAt < :cutoff)")
  int deleteClosedBefore(Instant cutoff);
}
