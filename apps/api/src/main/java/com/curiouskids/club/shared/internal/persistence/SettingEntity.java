package com.curiouskids.club.shared.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "setting")
public class SettingEntity {

  @Id private UUID id;

  @Column(name = "key", nullable = false, unique = true)
  private String key;

  @Column(nullable = false)
  private String value;

  @Column(name = "updated_by")
  private UUID updatedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected SettingEntity() {}

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }
}
