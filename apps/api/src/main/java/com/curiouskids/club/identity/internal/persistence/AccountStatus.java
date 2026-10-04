package com.curiouskids.club.identity.internal.persistence;

/** DISABLED accounts cannot log in (FR-ID-08 deletion, staff removal). */
public enum AccountStatus {
  ACTIVE,
  DISABLED
}
