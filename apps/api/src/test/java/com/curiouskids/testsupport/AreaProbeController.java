package com.curiouskids.testsupport;

import com.curiouskids.club.identity.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints in each protected area, so access rules can be checked before real staff and
 * admin endpoints exist. Registered only by the tests that import it.
 */
@RestController
public class AreaProbeController {

  @GetMapping("/api/v1/me/probe")
  String member(@AuthenticationPrincipal CurrentUser user) {
    return user.role();
  }

  @GetMapping("/api/v1/staff/probe")
  String staff() {
    return "staff";
  }

  @GetMapping("/api/v1/admin/probe")
  String admin() {
    return "admin";
  }

  @PostMapping("/api/v1/me/probe")
  String change() {
    return "changed";
  }
}
