package com.curiouskids.club.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

  private final PasswordPolicy policy = new PasswordPolicy();

  @ParameterizedTest
  @ValueSource(strings = {"short", "elevenchars"})
  @DisplayName("FR-ID-01: passwords under 12 characters are rejected")
  void tooShort(String password) {
    assertThat(policy.problem(password)).hasValue("Use at least 12 characters");
  }

  @ParameterizedTest
  @ValueSource(strings = {"passwordpassword", "PasswordPassword", "123456789012", "iloveyou1234"})
  @DisplayName("FR-ID-01: common passwords are rejected, whatever their case")
  void common(String password) {
    assertThat(policy.problem(password)).get().asString().contains("too common");
  }

  @Test
  @DisplayName("FR-ID-01: a long uncommon passphrase is accepted without composition rules")
  void passphraseAccepted() {
    assertThat(policy.problem("purple giraffes read slowly")).isEmpty();
  }

  @Test
  @DisplayName("FR-ID-01: length counts characters, not bytes, and is capped at 128")
  void lengthBounds() {
    assertThat(policy.problem("🦒".repeat(12))).isEmpty();
    assertThat(policy.problem("a".repeat(129))).hasValue("Use at most 128 characters");
  }
}
