package com.curiouskids.club.identity.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A single-use emailed token. Only the SHA-256 hash of the token is stored. */
@Entity
@Table(name = "verification_token")
public class VerificationTokenEntity {

  @Id private UUID id;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TokenType type;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected VerificationTokenEntity() {}

  public VerificationTokenEntity(
      UUID id, UUID accountId, TokenType type, String tokenHash, Instant now) {
    this.id = id;
    this.accountId = accountId;
    this.type = type;
    this.tokenHash = tokenHash;
    this.expiresAt = now.plus(type.lifetime());
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** Unused and not expired at {@code now}. */
  public boolean isUsable(Instant now) {
    return usedAt == null && now.isBefore(expiresAt);
  }

  public void markUsed(Instant now) {
    usedAt = now;
    updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
