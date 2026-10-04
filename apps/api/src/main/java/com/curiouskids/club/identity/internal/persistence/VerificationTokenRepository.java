package com.curiouskids.club.identity.internal.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface VerificationTokenRepository extends JpaRepository<VerificationTokenEntity, UUID> {

  /** Locks the token so two requests with the same link cannot both use it. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<VerificationTokenEntity> findByTokenHashAndType(String tokenHash, TokenType type);

  /** Removes an account's unused tokens of a type, so only the newest link works (FR-ID-02). */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "delete from VerificationTokenEntity t"
          + " where t.accountId = :accountId and t.type = :type and t.usedAt is null")
  int deleteUnused(UUID accountId, TokenType type);
}
