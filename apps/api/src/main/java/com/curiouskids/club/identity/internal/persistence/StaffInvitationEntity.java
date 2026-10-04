package com.curiouskids.club.identity.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** An admin's invitation to create a staff account (FR-ID-07). Only the token's hash is stored. */
@Entity
@Table(name = "staff_invitation")
public class StaffInvitationEntity {

  /** How long an invitation link works (security-and-privacy.md). */
  public static final Duration LIFETIME = Duration.ofDays(7);

  @Id private UUID id;

  @Column(nullable = false)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "invited_by")
  private UUID invitedBy;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "account_id")
  private UUID accountId;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected StaffInvitationEntity() {}

  /**
   * @param invitedBy the inviting admin, or null for the invitation sent at first start
   */
  public StaffInvitationEntity(
      UUID id, String email, Role role, String tokenHash, UUID invitedBy, Instant now) {
    if (role == Role.MEMBER) {
      throw new IllegalArgumentException("Only VOLUNTEER or ADMIN can be invited");
    }
    this.id = id;
    this.email = email;
    this.role = role;
    this.tokenHash = tokenHash;
    this.invitedBy = invitedBy;
    this.expiresAt = now.plus(LIFETIME);
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** Neither accepted, revoked nor expired at {@code now}. */
  public boolean isUsable(Instant now) {
    return acceptedAt == null && revokedAt == null && now.isBefore(expiresAt);
  }

  public void accept(UUID accountId, Instant now) {
    this.accountId = accountId;
    acceptedAt = now;
    updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public Role getRole() {
    return role;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
