package com.curiouskids.club.identity.internal;

import com.curiouskids.club.identity.EmailVerificationRequested;
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
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registers families and verifies their email (FR-ID-01, FR-ID-02). Responses never reveal whether
 * an email is registered, and logs carry account IDs only.
 */
@Service
public class RegistrationService {

  private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

  private final AccountRepository accounts;
  private final FamilyRepository families;
  private final VerificationTokenRepository tokens;
  private final PasswordPolicy passwordPolicy;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate transaction;
  private final IdentityProperties properties;
  private final Ids ids;
  private final Clock clock;

  RegistrationService(
      AccountRepository accounts,
      FamilyRepository families,
      VerificationTokenRepository tokens,
      PasswordPolicy passwordPolicy,
      PasswordEncoder passwordEncoder,
      ApplicationEventPublisher events,
      TransactionTemplate transaction,
      IdentityProperties properties,
      Ids ids,
      Clock clock) {
    this.accounts = accounts;
    this.families = families;
    this.tokens = tokens;
    this.passwordPolicy = passwordPolicy;
    this.passwordEncoder = passwordEncoder;
    this.events = events;
    this.transaction = transaction;
    this.properties = properties;
    this.ids = ids;
    this.clock = clock;
  }

  /** What a parent submits to register; the request layer has checked consent and formats. */
  public record Registration(String email, String password, String name, String phone) {}

  /**
   * Creates an unverified member and queues the verification email, or does nothing if the email is
   * already registered. Either way the caller sees the same outcome.
   *
   * @throws ApiException VALIDATION_FAILED if the password is too weak
   */
  public void register(Registration registration) {
    passwordPolicy
        .problem(registration.password())
        .ifPresent(
            message -> {
              throw new ApiException(
                  ErrorCode.VALIDATION_FAILED,
                  "Choose a stronger password.",
                  List.of(new ApiException.FieldError("password", message)));
            });
    String email = normalise(registration.email());
    // Hash before looking the email up, so known and unknown emails take similar time.
    String passwordHash = passwordEncoder.encode(registration.password());
    try {
      transaction.executeWithoutResult(status -> createIfNew(registration, email, passwordHash));
    } catch (DataIntegrityViolationException e) {
      // Someone registered the same email at the same moment: same outcome as an existing email.
      log.info("Registration raced with another for the same email; ignored");
    }
  }

  private void createIfNew(Registration registration, String email, String passwordHash) {
    if (accounts.findByEmail(email).isPresent()) {
      log.info("Registration for an existing email ignored");
      return;
    }
    Instant now = clock.instant();
    FamilyEntity family =
        families.save(
            new FamilyEntity(
                ids.next(), registration.name().strip(), blankToNull(registration.phone()), now));
    AccountEntity account =
        accounts.saveAndFlush(
            AccountEntity.member(
                ids.next(), email, passwordHash, family.getId(), properties.consentVersion(), now));
    issueVerification(account, now);
    log.atInfo()
        .addKeyValue("accountId", account.getId())
        .addKeyValue("familyId", family.getId())
        .log("Family registered");
  }

  /**
   * Verifies the account the token was issued for and uses up the token.
   *
   * @throws ApiException TOKEN_INVALID if the token is unknown, expired, used or replaced
   */
  public void verifyEmail(String token) {
    transaction.executeWithoutResult(
        status -> {
          Instant now = clock.instant();
          VerificationTokenEntity verification =
              tokens
                  .findByTokenHashAndType(Tokens.hash(token), TokenType.EMAIL_VERIFY)
                  .filter(t -> t.isUsable(now))
                  .orElseThrow(RegistrationService::invalidToken);
          AccountEntity account =
              accounts
                  .findById(verification.getAccountId())
                  .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                  .orElseThrow(RegistrationService::invalidToken);
          account.markEmailVerified(now);
          verification.markUsed(now);
          log.atInfo().addKeyValue("accountId", account.getId()).log("Email verified");
        });
  }

  /**
   * Sends a new verification link to an active, unverified account and invalidates older links.
   * Does nothing for unknown or already verified emails, without saying so.
   */
  public void resendVerification(String rawEmail) {
    String email = normalise(rawEmail);
    transaction.executeWithoutResult(
        status ->
            accounts
                .findByEmail(email)
                .filter(a -> !a.isEmailVerified() && a.getStatus() == AccountStatus.ACTIVE)
                .ifPresent(
                    account -> {
                      tokens.deleteUnused(account.getId(), TokenType.EMAIL_VERIFY);
                      issueVerification(account, clock.instant());
                      log.atInfo()
                          .addKeyValue("accountId", account.getId())
                          .log("Verification email re-sent");
                    }));
  }

  private void issueVerification(AccountEntity account, Instant now) {
    String token = Tokens.newToken();
    VerificationTokenEntity entity =
        tokens.save(
            new VerificationTokenEntity(
                ids.next(), account.getId(), TokenType.EMAIL_VERIFY, Tokens.hash(token), now));
    events.publishEvent(
        new EmailVerificationRequested(
            account.getId(), account.getEmail(), entity.getId(), token, entity.getExpiresAt()));
  }

  static String normalise(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static ApiException invalidToken() {
    return new ApiException(
        ErrorCode.TOKEN_INVALID, "This link has expired or was already used. Ask for a new one.");
  }
}
