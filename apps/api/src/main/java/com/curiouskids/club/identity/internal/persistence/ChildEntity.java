package com.curiouskids.club.identity.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A child of a family: first name or nickname and age band only (BR-30). */
@Entity
@Table(name = "child")
public class ChildEntity {

  @Id private UUID id;

  @Column(name = "family_id", nullable = false, updatable = false)
  private UUID familyId;

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "age_band", nullable = false)
  private String ageBand;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected ChildEntity() {}

  public ChildEntity(UUID id, UUID familyId, String firstName, String ageBand, Instant now) {
    this.id = id;
    this.familyId = familyId;
    this.firstName = firstName;
    this.ageBand = ageBand;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void update(String firstName, String ageBand, Instant now) {
    if (firstName != null) {
      this.firstName = firstName;
    }
    if (ageBand != null) {
      this.ageBand = ageBand;
    }
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getFamilyId() {
    return familyId;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getAgeBand() {
    return ageBand;
  }
}
