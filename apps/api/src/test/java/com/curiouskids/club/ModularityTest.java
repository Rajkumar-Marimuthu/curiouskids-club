package com.curiouskids.club;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

  @Test
  @DisplayName("NFR-11: modules depend only on allowed modules' public APIs, with no cycles")
  void verifiesModuleBoundaries() {
    ApplicationModules.of(ClubApplication.class).verify();
  }
}
