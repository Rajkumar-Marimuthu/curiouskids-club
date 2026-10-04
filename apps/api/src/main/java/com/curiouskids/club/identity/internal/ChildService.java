package com.curiouskids.club.identity.internal;

import com.curiouskids.club.identity.internal.persistence.ChildEntity;
import com.curiouskids.club.identity.internal.persistence.ChildRepository;
import com.curiouskids.club.identity.internal.persistence.FamilyRepository;
import com.curiouskids.club.shared.ApiException;
import com.curiouskids.club.shared.ErrorCode;
import com.curiouskids.club.shared.Ids;
import com.curiouskids.club.shared.SettingKeys;
import com.curiouskids.club.shared.Settings;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A family's child profiles (FR-ID-05): first name and age band only (BR-30), at most {@code
 * limits.max-children} (BR-15). Every method takes the caller's family from the session, and a
 * child of another family is reported as not found. Logs carry IDs only, never names.
 */
@Service
public class ChildService {

  private static final Logger log = LoggerFactory.getLogger(ChildService.class);

  private final ChildRepository children;
  private final FamilyRepository families;
  private final Settings settings;
  private final Ids ids;
  private final Clock clock;

  ChildService(
      ChildRepository children,
      FamilyRepository families,
      Settings settings,
      Ids ids,
      Clock clock) {
    this.children = children;
    this.families = families;
    this.settings = settings;
    this.ids = ids;
    this.clock = clock;
  }

  public record Child(UUID id, String firstName, String ageBand) {}

  @Transactional(readOnly = true)
  public List<Child> list(UUID familyId) {
    return children.findByFamilyIdOrderByCreatedAtAscIdAsc(familyId).stream()
        .map(ChildService::toChild)
        .toList();
  }

  /**
   * Adds a child under the family.
   *
   * @throws ApiException LIMIT_REACHED if the family already has the maximum number of children
   */
  @Transactional
  public Child add(UUID familyId, String firstName, String ageBand) {
    // Lock the family so two adds at the same moment cannot both pass the count.
    families.findLockedById(familyId).orElseThrow(ChildService::notFound);
    int max = settings.getInt(SettingKeys.LIMITS_MAX_CHILDREN);
    if (children.countByFamilyId(familyId) >= max) {
      throw new ApiException(
          ErrorCode.LIMIT_REACHED, "A family can have at most " + max + " children.");
    }
    ChildEntity child =
        children.save(
            new ChildEntity(ids.next(), familyId, firstName.strip(), ageBand, clock.instant()));
    log.atInfo()
        .addKeyValue("familyId", familyId)
        .addKeyValue("childId", child.getId())
        .log("Child added");
    return toChild(child);
  }

  /**
   * Changes the given fields; a null field is left as it is.
   *
   * @throws ApiException NOT_FOUND if the family has no such child
   */
  @Transactional
  public Child update(UUID familyId, UUID childId, String firstName, String ageBand) {
    ChildEntity child =
        children.findByIdAndFamilyId(childId, familyId).orElseThrow(ChildService::notFound);
    child.update(firstName == null ? null : firstName.strip(), ageBand, clock.instant());
    return toChild(child);
  }

  /**
   * Removes the child.
   *
   * @throws ApiException NOT_FOUND if the family has no such child
   */
  @Transactional
  public void delete(UUID familyId, UUID childId) {
    ChildEntity child =
        children.findByIdAndFamilyId(childId, familyId).orElseThrow(ChildService::notFound);
    children.delete(child);
    log.atInfo()
        .addKeyValue("familyId", familyId)
        .addKeyValue("childId", childId)
        .log("Child removed");
  }

  private static Child toChild(ChildEntity entity) {
    return new Child(entity.getId(), entity.getFirstName(), entity.getAgeBand());
  }

  private static ApiException notFound() {
    return new ApiException(ErrorCode.NOT_FOUND, "Child not found.");
  }
}
