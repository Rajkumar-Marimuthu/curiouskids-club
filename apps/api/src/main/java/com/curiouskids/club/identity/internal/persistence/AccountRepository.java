package com.curiouskids.club.identity.internal.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

  /** Emails are stored lower-cased, so pass a normalised email. */
  Optional<AccountEntity> findByEmail(String email);
}
