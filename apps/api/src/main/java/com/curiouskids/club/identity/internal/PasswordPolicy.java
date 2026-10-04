package com.curiouskids.club.identity.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * FR-ID-01: at least 12 characters and not a commonly used password. No composition rules
 * (security-and-privacy.md). Length limits are also declared on the request for the contract.
 */
@Component
public class PasswordPolicy {

  static final int MIN_LENGTH = 12;
  static final int MAX_LENGTH = 128;

  private static final String COMMON_LIST = "identity/common-passwords.txt";

  private final Set<String> common = load();

  /** Why the password is not allowed, or empty if it is fine. */
  public Optional<String> problem(String password) {
    int length = password.codePointCount(0, password.length());
    if (length < MIN_LENGTH) {
      return Optional.of("Use at least " + MIN_LENGTH + " characters");
    }
    if (length > MAX_LENGTH) {
      return Optional.of("Use at most " + MAX_LENGTH + " characters");
    }
    if (common.contains(password.toLowerCase(Locale.ROOT))) {
      return Optional.of("This password is too common. Choose a less predictable one");
    }
    return Optional.empty();
  }

  private static Set<String> load() {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                new ClassPathResource(COMMON_LIST).getInputStream(), StandardCharsets.UTF_8))) {
      return reader
          .lines()
          .filter(line -> !line.isBlank() && !line.startsWith("#"))
          .collect(Collectors.toUnmodifiableSet());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
