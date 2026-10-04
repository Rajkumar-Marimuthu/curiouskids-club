package com.curiouskids.club.identity.internal;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Argon2id behind a delegating encoder (security-and-privacy.md): hashes are stored with an {@code
 * {argon2}} prefix, so the algorithm or its cost can change later without breaking stored hashes.
 */
@Configuration(proxyBeanMethods = false)
class PasswordConfig {

  @Bean
  PasswordEncoder passwordEncoder() {
    return new DelegatingPasswordEncoder(
        "argon2", Map.of("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
  }
}
