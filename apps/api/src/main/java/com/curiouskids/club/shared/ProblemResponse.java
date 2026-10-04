package com.curiouskids.club.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Documents the RFC 9457 problem+json body every error has, for the contract and the generated web
 * client. The body itself is written by the global exception handler.
 */
@Schema(name = "Problem")
public record ProblemResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String type,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int status,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ErrorCode code,
    String detail,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String traceId,
    List<Field> errors) {

  /** One invalid field. */
  @Schema(name = "ProblemFieldError")
  public record Field(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String field,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {}
}
