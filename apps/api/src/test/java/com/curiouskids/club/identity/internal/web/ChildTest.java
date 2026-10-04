package com.curiouskids.club.identity.internal.web;

import static com.curiouskids.club.support.Csrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.curiouskids.club.identity.internal.ChildService;
import com.curiouskids.club.shared.ApiException;
import com.curiouskids.club.shared.ErrorCode;
import com.curiouskids.club.support.IntegrationTest;
import com.curiouskids.club.support.OtherFamily;
import com.curiouskids.club.support.TestAccounts;
import com.curiouskids.club.support.TestAccounts.Account;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class ChildTest extends IntegrationTest {

  @Autowired TestAccounts accounts;
  @Autowired ChildService children;
  @Autowired JdbcClient jdbc;
  @Autowired JsonMapper json;

  @Test
  @DisplayName("FR-ID-05: a member adds, lists, changes and removes children under their family")
  void manageChildren() throws Exception {
    Account member = accounts.member(true);

    String id =
        send(member, post("/api/v1/me/children"), Map.of("firstName", " Ada ", "ageBand", "6-8"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.firstName").value("Ada"))
            .andExpect(jsonPath("$.ageBand").value("6-8"))
            .andReturn()
            .getResponse()
            .getContentAsString()
            .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    send(member, post("/api/v1/me/children"), Map.of("firstName", "Bo", "ageBand", "0-2"))
        .andExpect(status().isCreated());
    assertThat(
            jdbc.sql("select family_id from child where id = :id")
                .param("id", UUID.fromString(id))
                .query(UUID.class)
                .single())
        .isEqualTo(member.familyId());

    send(member, patch("/api/v1/me/children/" + id), Map.of("ageBand", "9-12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Ada"))
        .andExpect(jsonPath("$.ageBand").value("9-12"));

    mvc.perform(get("/api/v1/me/children").with(member.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].firstName").value("Ada"))
        .andExpect(jsonPath("$[0].ageBand").value("9-12"))
        .andExpect(jsonPath("$[1].firstName").value("Bo"));

    mvc.perform(delete("/api/v1/me/children/" + id).with(member.session()).with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/me/children").with(member.session()))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].firstName").value("Bo"));
  }

  @Test
  @DisplayName("FR-ID-05: with 6 children already, adding another fails with LIMIT_REACHED")
  void seventhChildRefused() throws Exception {
    Account member = accounts.member(true);
    for (int i = 0; i < 6; i++) {
      children.add(member.familyId(), "Child " + i, "3-5");
    }

    send(member, post("/api/v1/me/children"), Map.of("firstName", "Seven", "ageBand", "3-5"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("LIMIT_REACHED"));
    assertThat(children.list(member.familyId())).hasSize(6);
  }

  @Test
  @DisplayName("FR-ID-05, BR-15: simultaneous adds never take a family past 6 children")
  void concurrentAddsRespectLimit() throws Exception {
    Account member = accounts.member(true);
    for (int i = 0; i < 4; i++) {
      children.add(member.familyId(), "Child " + i, "3-5");
    }
    int threads = 8;
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Boolean>> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      for (int i = 0; i < threads; i++) {
        String name = "Racer " + i;
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    children.add(member.familyId(), name, "6-8");
                    return true;
                  } catch (ApiException e) {
                    assertThat(e.code()).isEqualTo(ErrorCode.LIMIT_REACHED);
                    return false;
                  }
                }));
      }
      start.countDown();
      int added = 0;
      for (Future<Boolean> result : results) {
        added += result.get() ? 1 : 0;
      }
      assertThat(added).isEqualTo(2);
    }
    assertThat(children.list(member.familyId())).hasSize(6);
  }

  @Test
  @DisplayName("FR-ID-05, BR-30: any other child field is rejected, on add and on change")
  void otherFieldsRejected() throws Exception {
    Account member = accounts.member(true);

    send(
            member,
            post("/api/v1/me/children"),
            Map.of("firstName", "Ada", "ageBand", "6-8", "surname", "Lovelace"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("surname"))
        .andExpect(jsonPath("$.errors[0].message").value("is not accepted"));
    String id = children.add(member.familyId(), "Ada", "6-8").id().toString();
    send(member, patch("/api/v1/me/children/" + id), Map.of("birthDate", "2019-01-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("birthDate"));

    assertThat(
            jdbc.sql("select count(*) from child where family_id = :f")
                .param("f", member.familyId())
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "FR-ID-05, BR-30: the first name is required (at most 40) and the age band is a known one")
  void fieldsValidated() throws Exception {
    Account member = accounts.member(true);

    send(member, post("/api/v1/me/children"), Map.of("firstName", " ", "ageBand", "6-8"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("firstName"));
    send(member, post("/api/v1/me/children"), Map.of("firstName", "A".repeat(41), "ageBand", "6-8"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("firstName"));
    send(member, post("/api/v1/me/children"), Map.of("firstName", "Ada", "ageBand", "7"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("ageBand"))
        .andExpect(jsonPath("$.errors[0].message").value("Choose an age band"));
    send(member, post("/api/v1/me/children"), Map.of("firstName", "Ada"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("ageBand"));
  }

  @Test
  @DisplayName("Access control: another family gets 404 for your child and cannot change it")
  void otherFamilyCannotReachChild() throws Exception {
    Account owner = accounts.member(true);
    Account intruder = accounts.member(true);
    UUID id = children.add(owner.familyId(), "Ada", "6-8").id();
    String body = json.writeValueAsString(Map.of("firstName", "Mallory"));

    OtherFamily.cannotReach(
        mvc,
        intruder,
        List.<MockHttpServletRequestBuilder>of(
            patch("/api/v1/me/children/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
            delete("/api/v1/me/children/" + id)));

    mvc.perform(get("/api/v1/me/children").with(intruder.session()))
        .andExpect(jsonPath("$.length()").value(0));
    assertThat(children.list(owner.familyId()))
        .singleElement()
        .isEqualTo(new ChildService.Child(id, "Ada", "6-8"));
  }

  @Test
  @DisplayName("FR-ID-05: child endpoints are for members; staff get 403, anonymous 401")
  void membersOnly() throws Exception {
    mvc.perform(get("/api/v1/me/children")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/me/children").with(accounts.volunteer().session()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/me/children").with(accounts.admin().session()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("NFR-06: logs about children carry IDs, never the child's name")
  void logsHoldNoNames(CapturedOutput output) throws Exception {
    Account member = accounts.member(true);
    send(member, post("/api/v1/me/children"), Map.of("firstName", "Zephyrine", "ageBand", "13+"))
        .andExpect(status().isCreated());

    assertThat(output).contains("Child added").doesNotContain("Zephyrine");
  }

  private ResultActions send(
      Account account, MockHttpServletRequestBuilder request, Map<String, ?> body)
      throws Exception {
    return mvc.perform(
        request
            .with(account.session())
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }
}
