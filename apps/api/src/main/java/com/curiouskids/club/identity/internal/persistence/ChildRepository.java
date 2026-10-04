package com.curiouskids.club.identity.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildRepository extends JpaRepository<ChildEntity, UUID> {

  List<ChildEntity> findByFamilyIdOrderByCreatedAtAscIdAsc(UUID familyId);

  long countByFamilyId(UUID familyId);

  /** The child only if it belongs to this family, so another family's child looks absent. */
  Optional<ChildEntity> findByIdAndFamilyId(UUID id, UUID familyId);
}
