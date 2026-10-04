package com.curiouskids.club.shared.internal.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness check for the web app and smoke tests; the first contract-driven endpoint. */
@RestController
@Tag(name = "ping")
class PingController {

  private final Clock clock;

  PingController(Clock clock) {
    this.clock = clock;
  }

  @GetMapping(path = "/api/v1/ping", produces = MediaType.APPLICATION_JSON_VALUE)
  PingResponse ping() {
    return new PingResponse("ok", clock.instant());
  }
}
