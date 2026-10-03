package com.curiouskids.club.shared.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.curiouskids.club.support.IntegrationTest;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class PingTest extends IntegrationTest {

  @Autowired JsonMapper json;

  @Test
  @DisplayName("NFR-01: GET /api/v1/ping returns ok and the server time")
  void ping() throws Exception {
    String body =
        mvc.perform(get("/api/v1/ping"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value("ok"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String time = json.readTree(body).get("time").asString();
    assertThat(Instant.parse(time)).isNotNull();
    assertThat(time).endsWith("Z");
  }

  @Test
  @DisplayName("NFR-09: request logs are JSON lines carrying the traceId")
  void logsAreJsonWithTraceId(CapturedOutput output) throws Exception {
    String traceId = "log-trace-0001";
    mvc.perform(get("/api/v1/ping").header(TraceIdFilter.HEADER, traceId))
        .andExpect(status().isOk());

    List<Map<String, Object>> requestLines =
        Arrays.stream(output.getOut().split("\\R"))
            .filter(line -> line.contains("GET /api/v1/ping 200"))
            .map(line -> json.readValue(line, new TypeReference<Map<String, Object>>() {}))
            .toList();

    assertThat(requestLines).isNotEmpty();
    assertThat(requestLines).anySatisfy(line -> assertThat(line).containsEntry("traceId", traceId));
  }
}
