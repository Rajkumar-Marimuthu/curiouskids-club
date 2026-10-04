package com.curiouskids.club.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokensTest {

  @Test
  @DisplayName("FR-ID-02: link tokens are random 256-bit, URL-safe, and stored only as a hash")
  void tokensAreRandomAndHashed() {
    String token = Tokens.newToken();

    assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
    assertThat(token).matches("[A-Za-z0-9_-]{43}");
    assertThat(Tokens.newToken()).isNotEqualTo(token);
    assertThat(Tokens.hash(token)).hasSize(64).isEqualTo(Tokens.hash(token)).isNotEqualTo(token);
  }
}
