package com.curiouskids.club;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.curiouskids.club.support.IntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The running API must match {@code contracts/openapi.yaml} exactly (ADR-0005). If this fails,
 * update the contract first, then the code; the assertion message shows both documents.
 */
class ContractTest extends IntegrationTest {

  private static final Path CONTRACT = Path.of("../../contracts/openapi.yaml");

  @Test
  @DisplayName("NFR-11: exported OpenAPI document equals contracts/openapi.yaml")
  void apiMatchesContract() throws Exception {
    String exported =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Map<String, Object> actual = new Yaml().load(exported);
    // Server URLs depend on where the API runs, so they are not part of the contract.
    actual.remove("servers");
    Map<String, Object> expected = new Yaml().load(Files.readString(CONTRACT));

    assertThat(actual).isEqualTo(expected);
  }
}
