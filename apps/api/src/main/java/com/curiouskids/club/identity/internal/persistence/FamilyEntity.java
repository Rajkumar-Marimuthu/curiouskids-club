package com.curiouskids.club.identity.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "family")
public class FamilyEntity {

  @Id private UUID id;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  private String phone;

  @Column(name = "flagged_at")
  private Instant flaggedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected FamilyEntity() {}

  public FamilyEntity(UUID id, String displayName, String phone, Instant now) {
    this.id = id;
    this.displayName = displayName;
    this.phone = phone;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getPhone() {
    return phone;
  }
}
