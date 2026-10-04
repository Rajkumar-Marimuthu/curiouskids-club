package com.curiouskids.club.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ErrorCodeTest {

  @ParameterizedTest
  @CsvSource({
    "400, VALIDATION_FAILED",
    "401, UNAUTHENTICATED",
    "403, FORBIDDEN",
    "404, NOT_FOUND",
    "405, METHOD_NOT_ALLOWED",
    "406, NOT_ACCEPTABLE",
    "409, CONFLICT",
    "413, VALIDATION_FAILED",
    "415, UNSUPPORTED_MEDIA_TYPE",
    "429, RATE_LIMITED",
    "500, INTERNAL_ERROR",
    "503, INTERNAL_ERROR"
  })
  @DisplayName("Framework errors map to a stable code")
  void mapsStatusToCode(int status, ErrorCode expected) {
    assertThat(ErrorCode.forStatus(status)).isEqualTo(expected);
  }
}
