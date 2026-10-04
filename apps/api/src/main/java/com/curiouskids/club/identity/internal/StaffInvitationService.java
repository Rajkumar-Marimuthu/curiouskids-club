package com.curiouskids.club.identity.internal;

import com.curiouskids.club.identity.StaffInvited;
import com.curiouskids.club.identity.internal.persistence.AccountEntity;
import com.curiouskids.club.identity.internal.persistence.AccountRepository;
import com.curiouskids.club.identity.internal.persistence.Role;
import com.curiouskids.club.identity.internal.persistence.StaffInvitationEntity;
import com.curiouskids.club.identity.internal.persistence.StaffInvitationRepository;
import com.curiouskids.club.shared.ApiException;
import com.curiouskids.club.shared.AuditEntry;
import com.curiouskids.club.shared.AuditLog;
import com.curiouskids.club.shared.ErrorCode;
import com.curiouskids.club.shared.Ids;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Staff accounts are created only by invitation (FR-ID-07, BR-31): an admin invites an email as
 * VOLUNTEER or ADMIN, and the person sets a password from the 7-day, single-use link. Invitations
 * are audited (FR-ADM-03). Logs and audit details carry IDs and roles only.
 */
@Service
public class StaffInvitationService {

  /** How long closed or expired invitations, and so their emails, are kept. */
  static final Duration KEEP = Duration.ofDays(30);

  private static final Logger log = LoggerFactory.getLogger(StaffInvitationService.class);

  private final StaffInvitationRepository invitations;
  private final AccountRepository accounts;
  private final PasswordPolicy passwordPolicy;
  private final PasswordEncoder passwordEncoder;
  private final AuditLog audit;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate transaction;
  private final Ids ids;
  private final Clock clock;

  StaffInvitationService(
      StaffInvitationRepository invitations,
      AccountRepository accounts,
      PasswordPolicy passwordPolicy,
      PasswordEncoder passwordEncoder,
      AuditLog audit,
      ApplicationEventPublisher events,
      TransactionTemplate transaction,
      Ids ids,
      Clock clock) {
    this.invitations = invitations;
    this.accounts = accounts;
    this.passwordPolicy = passwordPolicy;
    this.passwordEncoder = passwordEncoder;
    this.audit = audit;
    this.events = events;
    this.transaction = transaction;
    this.ids = ids;
    this.clock = clock;
  }

  /** An invitation as an admin sees it. */
  public record Invitation(UUID id, String email, String role, Instant expiresAt) {}

  /**
   * Invites an email to become VOLUNTEER or ADMIN and emails the link. A newer invitation for the
   * same email replaces the older one.
   *
   * @param adminId the inviting admin
   * @param role VOLUNTEER or ADMIN
   * @throws ApiException CONFLICT if the email already has an account, or another invitation for it
   *     was sent at the same moment
   */
  public Invitation invite(UUID adminId, String rawEmail, String role) {
    String email = RegistrationService.normalise(rawEmail);
    try {
      return transaction.execute(
          status -> {
            if (accounts.findByEmail(email).isPresent()) {
              throw new ApiException(
                  ErrorCode.CONFLICT,
                  "This email already has an account.",
                  List.of(
                      new ApiException.FieldError("email", "This email already has an account")));
            }
            return issue(email, Role.valueOf(role), adminId);
          });
    } catch (DataIntegrityViolationException e) {
      throw new ApiException(
          ErrorCode.CONFLICT, "Another invitation for this email was just sent. Try again.");
    }
  }

  /**
   * Sends an ADMIN invitation to {@code rawEmail} unless the club already has an admin or that
   * email already has an account or a working invitation. Used once at start-up.
   *
   * @return whether an invitation was sent
   */
  public boolean inviteFirstAdmin(String rawEmail) {
    String email = RegistrationService.normalise(rawEmail);
    try {
      Boolean sent =
          transaction.execute(
              status -> {
                Instant now = clock.instant();
                if (accounts.existsByRole(Role.ADMIN)
                    || accounts.findByEmail(email).isPresent()
                    || invitations.existsUsable(email, now)) {
                  return false;
                }
                issue(email, Role.ADMIN, null);
                return true;
              });
      return Boolean.TRUE.equals(sent);
    } catch (DataIntegrityViolationException e) {
      // Another instance sent it at the same moment.
      return false;
    }
  }

  /**
   * Creates the staff account the invitation is for, with the email already verified, and uses up
   * the link. The person then logs in.
   *
   * @throws ApiException VALIDATION_FAILED if the password is too weak; TOKEN_INVALID if the link
   *     is unknown, expired, used or replaced, or the email has gained an account meanwhile
   */
  public void accept(String token, String password) {
    passwordPolicy
        .problem(password)
        .ifPresent(
            message -> {
              throw new ApiException(
                  ErrorCode.VALIDATION_FAILED,
                  "Choose a stronger password.",
                  List.of(new ApiException.FieldError("password", message)));
            });
    String passwordHash = passwordEncoder.encode(password);
    try {
      transaction.executeWithoutResult(
          status -> {
            Instant now = clock.instant();
            StaffInvitationEntity invitation =
                invitations
                    .findByTokenHash(Tokens.hash(token))
                    .filter(i -> i.isUsable(now))
                    .orElseThrow(StaffInvitationService::invalidToken);
            if (accounts.findByEmail(invitation.getEmail()).isPresent()) {
              throw invalidToken();
            }
            AccountEntity account =
                accounts.saveAndFlush(
                    AccountEntity.staff(
                        ids.next(),
                        invitation.getEmail(),
                        passwordHash,
                        invitation.getRole(),
                        now));
            invitation.accept(account.getId(), now);
            audit.record(
                new AuditEntry(
                    account.getId(),
                    "STAFF_INVITATION_ACCEPTED",
                    "STAFF_INVITATION",
                    invitation.getId(),
                    Map.of("role", invitation.getRole().name(), "accountId", account.getId())));
            log.atInfo()
                .addKeyValue("invitationId", invitation.getId())
                .addKeyValue("accountId", account.getId())
                .log("Staff invitation accepted");
          });
    } catch (DataIntegrityViolationException e) {
      // The email registered at the same moment.
      throw invalidToken();
    }
  }

  /** Deletes invitations closed or expired more than 30 days ago; returns how many. */
  int purge() {
    return transaction.execute(
        status -> invitations.deleteClosedBefore(clock.instant().minus(KEEP)));
  }

  private Invitation issue(String email, Role role, UUID adminId) {
    Instant now = clock.instant();
    invitations.revokeOpen(email, now);
    String token = Tokens.newToken();
    StaffInvitationEntity invitation =
        invitations.saveAndFlush(
            new StaffInvitationEntity(ids.next(), email, role, Tokens.hash(token), adminId, now));
    events.publishEvent(
        new StaffInvited(invitation.getId(), email, role.name(), token, invitation.getExpiresAt()));
    audit.record(
        new AuditEntry(
            adminId,
            "STAFF_INVITED",
            "STAFF_INVITATION",
            invitation.getId(),
            Map.of("role", role.name())));
    log.atInfo()
        .addKeyValue("invitationId", invitation.getId())
        .addKeyValue("role", role.name())
        .log("Staff invited");
    return new Invitation(invitation.getId(), email, role.name(), invitation.getExpiresAt());
  }

  private static ApiException invalidToken() {
    return new ApiException(
        ErrorCode.TOKEN_INVALID,
        "This invitation has expired or was already used. Ask an admin for a new one.");
  }
}
