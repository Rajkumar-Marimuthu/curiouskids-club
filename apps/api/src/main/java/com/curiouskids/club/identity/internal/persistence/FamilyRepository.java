package com.curiouskids.club.identity.internal.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface FamilyRepository extends JpaRepository<FamilyEntity, UUID> {

  /** Locks the family row, so per-family limits (BR-15) hold under concurrent requests. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select f from FamilyEntity f where f.id = :id")
  Optional<FamilyEntity> findLockedById(UUID id);
}
