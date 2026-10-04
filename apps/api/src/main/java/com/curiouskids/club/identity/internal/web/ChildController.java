package com.curiouskids.club.identity.internal.web;

import com.curiouskids.club.identity.CurrentUser;
import com.curiouskids.club.identity.internal.ChildService;
import com.curiouskids.club.shared.ProblemResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The logged-in family's child profiles (FR-ID-05). Members only; the family comes from the
 * session.
 */
@RestController
@Tag(name = "family")
class ChildController {

  private static final String PROBLEM = "application/problem+json";

  private final ChildService children;

  ChildController(ChildService children) {
    this.children = children;
  }

  @GetMapping(path = "/api/v1/me/children", produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(responseCode = "200", description = "The family's children, oldest profile first")
  List<ChildResponse> list(@AuthenticationPrincipal CurrentUser user) {
    return children.list(user.familyId()).stream().map(ChildController::toResponse).toList();
  }

  @PostMapping(path = "/api/v1/me/children", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @ApiResponse(responseCode = "201", description = "Child added")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_FAILED with field errors, including any field other than these",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "422",
      description = "LIMIT_REACHED: the family already has the most children allowed",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  ChildResponse add(
      @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ChildRequest request) {
    return toResponse(children.add(user.familyId(), request.firstName(), request.ageBand()));
  }

  @PatchMapping(path = "/api/v1/me/children/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(responseCode = "200", description = "Child changed")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_FAILED with field errors, including any field other than these",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "NOT_FOUND: no such child in your family",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  ChildResponse update(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID id,
      @Valid @RequestBody ChildUpdateRequest request) {
    return toResponse(children.update(user.familyId(), id, request.firstName(), request.ageBand()));
  }

  @DeleteMapping("/api/v1/me/children/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiResponse(responseCode = "204", description = "Child removed")
  @ApiResponse(
      responseCode = "404",
      description = "NOT_FOUND: no such child in your family",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
    children.delete(user.familyId(), id);
  }

  private static ChildResponse toResponse(ChildService.Child child) {
    return new ChildResponse(child.id(), child.firstName(), child.ageBand());
  }
}
