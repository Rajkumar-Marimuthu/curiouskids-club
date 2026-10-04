package com.curiouskids.club.identity.internal;

import com.curiouskids.club.identity.EmailChangeRequested;
import com.curiouskids.club.identity.EmailChanged;
import com.curiouskids.club.identity.internal.persistence.AccountEntity;
import com.curiouskids.club.identity.internal.persistence.AccountRepository;
import com.curiouskids.club.identity.internal.persistence.AccountStatus;
import com.curiouskids.club.identity.internal.persistence.FamilyEntity;
import com.curiouskids.club.identity.internal.persistence.FamilyRepository;
import com.curiouskids.club.identity.internal.persistence.TokenType;
import com.curiouskids.club.identity.internal.persistence.VerificationTokenEntity;
import com.curiouskids.club.identity.internal.persistence.VerificationTokenRepository;
import com.curiouskids.club.shared.ApiException;
import com.curiouskids.club.shared.ErrorCode;
import com.curiouskids.club.shared.Ids;
import com.curiouskids.club.shared.RateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A member's own profile and preferences (FR-ID-06): name, phone and reminder toggles (BR-36),
 * password change, and email change confirmed from the new address. Changing the password or email
 * needs the current password, and wrong guesses count towards the login limit (FR-ID-03). Logs
 * carry account IDs only.
 */
@Service
public class ProfileService {

  private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

  private final AccountRepository accounts;
  private final FamilyRepository families;
  private final VerificationTokenRepository tokens;
  private final PasswordPolicy passwordPolicy;
  private final PasswordEncoder passwordEncoder;
  private final FindByIndexNameSessionRepository<? extends Session> sessions;
  private final LoginThrottle loginThrottle;
  private final RequestThrottle requestThrottle;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate transaction;
  private final Ids ids;
  private final Clock clock;

  ProfileService(
      AccountRepository accounts,
      FamilyRepository families,
      VerificationTokenRepository tokens,
      PasswordPolicy passwordPolicy,
      PasswordEncoder passwordEncoder,
      FindByIndexNameSessionRepository<? extends Session> sessions,
      LoginThrottle loginThrottle,
      RequestThrottle requestThrottle,
      ApplicationEventPublisher events,
      TransactionTemplate transaction,
      Ids ids,
      Clock clock) {
    this.accounts = accounts;
    this.families = families;
    this.tokens = tokens;
    this.passwordPolicy = passwordPolicy;
    this.passwordEncoder = passwordEncoder;
    this.sessions = sessions;
    this.loginThrottle = loginThrottle;
    this.requestThrottle = requestThrottle;
    this.events = events;
    this.transaction = transaction;
    this.ids = ids;
    this.clock = clock;
  }

  /** What the member sees and can change about their account. */
  public record Profile(
      String name,
      String phone,
      String email,
      String pendingEmail,
      boolean remindPickup,
      boolean remindDueSoon) {}

  /**
   * The fields to change; null leaves a field as it is. A blank phone removes it. The request layer
   * has checked lengths and formats.
   */
  public record ProfileChange(
      String name, String phone, Boolean remindPickup, Boolean remindDueSoon) {}

  public Profile get(UUID accountId) {
    return transaction.execute(status -> profile(member(accountId)));
  }

  public Profile update(UUID accountId, ProfileChange change) {
    return transaction.execute(
        status -> {
          Instant now = clock.instant();
          AccountEntity account = member(accountId);
          if (change.name() != null || change.phone() != null) {
            FamilyEntity family = families.findLockedById(account.getFamilyId()).orElseThrow();
            family.changeContact(
                change.name() == null ? family.getDisplayName() : change.name().strip(),
                change.phone() == null ? family.getPhone() : blankToNull(change.phone()),
                now);
          }
          account.setReminders(
              change.remindPickup() == null ? account.isRemindPickup() : change.remindPickup(),
              change.remindDueSoon() == null ? account.isRemindDueSoon() : change.remindDueSoon(),
              now);
          log.atInfo().addKeyValue("accountId", accountId).log("Profile changed");
          return profile(account);
        });
  }

  /**
   * Sets a new password and ends every other session of the account; the caller's session stays.
   *
   * @param keepSessionId the session the request came in on, or null
   * @throws ApiException VALIDATION_FAILED if the new password is too weak or the current one is
   *     wrong; RATE_LIMITED after too many wrong passwords
   */
  public void changePassword(
      UUID accountId,
      String currentPassword,
      String newPassword,
      String keepSessionId,
      String clientAddress) {
    passwordPolicy
        .problem(newPassword)
        .ifPresent(
            message -> {
              throw fieldError("Choose a stronger password.", "newPassword", message);
            });
    AccountEntity account = transaction.execute(status -> member(accountId));
    checkCurrentPassword(account, currentPassword, clientAddress);
    String passwordHash = passwordEncoder.encode(newPassword);
    transaction.executeWithoutResult(
        status -> {
          Instant now = clock.instant();
          member(accountId).changePassword(passwordHash, now);
          // A reset link sent before the change must not undo it.
          tokens.deleteUnused(accountId, TokenType.PASSWORD_RESET);
          sessions.findByPrincipalName(accountId.toString()).keySet().stream()
              .filter(id -> !id.equals(keepSessionId))
              .forEach(sessions::deleteById);
          log.atInfo().addKeyValue("accountId", accountId).log("Password changed");
        });
  }

  /**
   * Starts a move to a new email address: the account keeps its current address until the link sent
   * to the new one is used, and a newer request replaces an older one. If the new address already
   * has an account, no link is sent, without saying so.
   *
   * @throws ApiException VALIDATION_FAILED if the address is the current one or the password is
   *     wrong; RATE_LIMITED after too many wrong passwords or emailed links
   */
  public void requestEmailChange(
      UUID accountId, String rawNewEmail, String currentPassword, String clientAddress) {
    String newEmail = RegistrationService.normalise(rawNewEmail);
    AccountEntity current = transaction.execute(status -> member(accountId));
    if (newEmail.equals(current.getEmail())) {
      throw fieldError(
          "That is already your email address.", "newEmail", "This is already your email address");
    }
    checkCurrentPassword(current, currentPassword, clientAddress);
    requestThrottle.emailLink(newEmail, clientAddress);
    transaction.executeWithoutResult(
        status -> {
          Instant now = clock.instant();
          // Clears the persistence context, so it runs before the account is loaded.
          tokens.deleteUnused(accountId, TokenType.EMAIL_CHANGE);
          AccountEntity account = member(accountId);
          account.requestEmailChange(newEmail, now);
          if (accounts.findByEmail(newEmail).isPresent()) {
            log.atInfo()
                .addKeyValue("accountId", accountId)
                .log("Email change to a registered address; no link sent");
            return;
          }
          String token = Tokens.newToken();
          VerificationTokenEntity entity =
              tokens.save(
                  new VerificationTokenEntity(
                      ids.next(), accountId, TokenType.EMAIL_CHANGE, Tokens.hash(token), now));
          events.publishEvent(
              new EmailChangeRequested(
                  accountId, newEmail, entity.getId(), token, entity.getExpiresAt()));
          log.atInfo().addKeyValue("accountId", accountId).log("Email change requested");
        });
  }

  /**
   * Moves the account to the address the link was sent to, marks it verified, uses up the link and
   * tells the previous address.
   *
   * @throws ApiException TOKEN_INVALID if the link is unknown, expired, used or replaced, or the
   *     address now belongs to another account
   */
  public void confirmEmailChange(String token) {
    try {
      transaction.executeWithoutResult(
          status -> {
            Instant now = clock.instant();
            VerificationTokenEntity link =
                tokens
                    .findByTokenHashAndType(Tokens.hash(token), TokenType.EMAIL_CHANGE)
                    .filter(t -> t.isUsable(now))
                    .orElseThrow(ProfileService::invalidToken);
            AccountEntity account =
                accounts
                    .findById(link.getAccountId())
                    .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                    .filter(a -> a.getPendingEmail() != null)
                    .orElseThrow(ProfileService::invalidToken);
            if (accounts.findByEmail(account.getPendingEmail()).isPresent()) {
              throw addressTaken();
            }
            String previousEmail = account.getEmail();
            link.markUsed(now);
            account.confirmEmailChange(now);
            accounts.flush();
            // Reset links went to the previous address; they must not work after the move.
            tokens.deleteUnused(account.getId(), TokenType.PASSWORD_RESET);
            loginThrottle.clear(Tokens.hash(previousEmail));
            events.publishEvent(new EmailChanged(account.getId(), previousEmail, link.getId()));
            log.atInfo().addKeyValue("accountId", account.getId()).log("Email changed");
          });
    } catch (DataIntegrityViolationException e) {
      // Another account took the address at the same moment.
      throw addressTaken();
    }
  }

  private void checkCurrentPassword(AccountEntity account, String password, String clientAddress) {
    String emailHash = Tokens.hash(account.getEmail());
    String ipHash = Tokens.hash(clientAddress);
    Optional<Duration> wait = loginThrottle.blockedFor(emailHash, ipHash);
    if (wait.isPresent()) {
      throw new RateLimitedException(
          "Too many attempts. Please wait a few minutes and try again.", wait.get());
    }
    if (!passwordEncoder.matches(password, account.getPasswordHash())) {
      loginThrottle.recordFailure(emailHash, ipHash);
      log.atInfo().addKeyValue("accountId", account.getId()).log("Current password wrong");
      throw fieldError(
          "Your current password is not correct.", "currentPassword", "Not your current password");
    }
  }

  private AccountEntity member(UUID accountId) {
    return accounts
        .findById(accountId)
        .filter(a -> a.getStatus() == AccountStatus.ACTIVE && a.getFamilyId() != null)
        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Please log in."));
  }

  private Profile profile(AccountEntity account) {
    FamilyEntity family = families.findById(account.getFamilyId()).orElseThrow();
    return new Profile(
        family.getDisplayName(),
        family.getPhone(),
        account.getEmail(),
        account.getPendingEmail(),
        account.isRemindPickup(),
        account.isRemindDueSoon());
  }

  private static ApiException fieldError(String detail, String field, String message) {
    return new ApiException(
        ErrorCode.VALIDATION_FAILED, detail, List.of(new ApiException.FieldError(field, message)));
  }

  private static String blankToNull(String value) {
    return value.isBlank() ? null : value.strip();
  }

  private static ApiException invalidToken() {
    return new ApiException(
        ErrorCode.TOKEN_INVALID, "This link has expired or was already used. Ask for a new one.");
  }

  private static ApiException addressTaken() {
    return new ApiException(
        ErrorCode.TOKEN_INVALID,
        "This email address already has an account, so the change was not made.");
  }
}
