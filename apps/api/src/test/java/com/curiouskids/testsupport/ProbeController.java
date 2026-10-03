package com.curiouskids.testsupport;

import com.curiouskids.club.shared.ApiException;
import com.curiouskids.club.shared.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints that raise each kind of error. Registered only by ErrorHandlingTest. */
@RestController
public class ProbeController {

  record ProbeRequest(@NotBlank String name) {}

  @PostMapping("/test/probe")
  String post(@Valid @RequestBody ProbeRequest request) {
    return request.name();
  }

  @GetMapping("/test/probe")
  String get(@RequestParam @Max(10) int size) {
    return Integer.toString(size);
  }

  @GetMapping("/test/probe/slot-full")
  String slotFull() {
    throw new ApiException(ErrorCode.SLOT_FULL, "The 17:00 window has no places left.");
  }

  @GetMapping("/test/probe/boom")
  String boom() {
    throw new IllegalStateException("secret internals");
  }
}
