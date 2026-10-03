package com.curiouskids.club.shared.internal.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<SettingEntity, UUID> {}
