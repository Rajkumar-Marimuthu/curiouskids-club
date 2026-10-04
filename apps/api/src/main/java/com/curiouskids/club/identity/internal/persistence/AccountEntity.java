package com.curiouskids.club.identity.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account")
public class AccountEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Column(name = "family_id")
  private UUID familyId;

  @Column(name = "email_verified_at")
  private Instant emailVerifiedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AccountStatus status;

  @Column(name = "consent_version")
  private String consentVersion;

  @Column(name = "consent_at")
  private Instant consentAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected AccountEntity() {}

  /** A new, unverified member of a family who accepted the given terms (BR-28). */
  public static AccountEntity member(
      UUID id,
      String email,
      String passwordHash,
      UUID familyId,
      String consentVersion,
      Instant now) {
    AccountEntity account = new AccountEntity();
    account.id = id;
    account.email = email;
    account.passwordHash = passwordHash;
    account.role = Role.MEMBER;
    account.familyId = familyId;
    account.status = AccountStatus.ACTIVE;
    account.consentVersion = consentVersion;
    account.consentAt = now;
    account.createdAt = now;
    account.updatedAt = now;
    return account;
  }

  public void changePassword(String passwordHash, Instant now) {
    this.passwordHash = passwordHash;
    updatedAt = now;
  }

  public void markEmailVerified(Instant now) {
    if (emailVerifiedAt == null) {
      emailVerifiedAt = now;
      updatedAt = now;
    }
  }

  public boolean isEmailVerified() {
    return emailVerifiedAt != null;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public UUID getFamilyId() {
    return familyId;
  }

  public AccountStatus getStatus() {
    return status;
  }

  public String getConsentVersion() {
    return consentVersion;
  }

  public Instant getConsentAt() {
    return consentAt;
  }

  public Instant getEmailVerifiedAt() {
    return emailVerifiedAt;
  }
}
