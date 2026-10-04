package com.curiouskids.club.identity.internal;

import com.curiouskids.club.identity.PasswordResetRequested;
import com.curiouskids.club.identity.internal.persistence.AccountEntity;
import com.curiouskids.club.identity.internal.persistence.AccountRepository;
import com.curiouskids.club.identity.internal.persistence.AccountStatus;
import com.curiouskids.club.identity.internal.persistence.TokenType;
import com.curiouskids.club.identity.internal.persistence.VerificationTokenEntity;
import com.curiouskids.club.identity.internal.persistence.VerificationTokenRepository;
import com.curiouskids.club.shared.ApiException;
import com.curiouskids.club.shared.ErrorCode;
import com.curiouskids.club.shared.Ids;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Password reset by emailed link (FR-ID-04). Requests never reveal whether an email is registered;
 * a completed reset ends every session of the account. Logs carry account IDs only.
 */
@Service
public class PasswordResetService {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

  private final AccountRepository accounts;
  private final VerificationTokenRepository tokens;
  private final PasswordPolicy passwordPolicy;
  private final PasswordEncoder passwordEncoder;
  private final FindByIndexNameSessionRepository<? extends Session> sessions;
  private final RequestThrottle requestThrottle;
  private final LoginThrottle loginThrottle;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate transaction;
  private final Ids ids;
  private final Clock clock;

  PasswordResetService(
      AccountRepository accounts,
      VerificationTokenRepository tokens,
      PasswordPolicy passwordPolicy,
      PasswordEncoder passwordEncoder,
      FindByIndexNameSessionRepository<? extends Session> sessions,
      RequestThrottle requestThrottle,
      LoginThrottle loginThrottle,
      ApplicationEventPublisher events,
      TransactionTemplate transaction,
      Ids ids,
      Clock clock) {
    this.accounts = accounts;
    this.tokens = tokens;
    this.passwordPolicy = passwordPolicy;
    this.passwordEncoder = passwordEncoder;
    this.sessions = sessions;
    this.requestThrottle = requestThrottle;
    this.loginThrottle = loginThrottle;
    this.events = events;
    this.transaction = transaction;
    this.ids = ids;
    this.clock = clock;
  }

  /**
   * Emails a one-hour reset link to an active account and invalidates older links. Does nothing for
   * unknown or disabled accounts, without saying so.
   *
   * @throws com.curiouskids.club.shared.RateLimitedException if too many links were asked for
   */
  public void request(String rawEmail, String clientAddress) {
    String email = RegistrationService.normalise(rawEmail);
    requestThrottle.emailLink(email, clientAddress);
    transaction.executeWithoutResult(
        status ->
            accounts
                .findByEmail(email)
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .ifPresentOrElse(
                    this::issueReset,
                    () -> log.info("Password reset for an unknown email ignored")));
  }

  /**
   * Sets a new password from a reset link, uses up the link, marks the email verified and ends all
   * the account's sessions.
   *
   * @throws ApiException VALIDATION_FAILED if the password is too weak; TOKEN_INVALID if the link
   *     is unknown, expired, used or replaced
   */
  public void confirm(String token, String newPassword) {
    passwordPolicy
        .problem(newPassword)
        .ifPresent(
            message -> {
              throw new ApiException(
                  ErrorCode.VALIDATION_FAILED,
                  "Choose a stronger password.",
                  List.of(new ApiException.FieldError("password", message)));
            });
    String passwordHash = passwordEncoder.encode(newPassword);
    transaction.executeWithoutResult(
        status -> {
          Instant now = clock.instant();
          VerificationTokenEntity reset =
              tokens
                  .findByTokenHashAndType(Tokens.hash(token), TokenType.PASSWORD_RESET)
                  .filter(t -> t.isUsable(now))
                  .orElseThrow(PasswordResetService::invalidToken);
          AccountEntity account =
              accounts
                  .findById(reset.getAccountId())
                  .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                  .orElseThrow(PasswordResetService::invalidToken);
          reset.markUsed(now);
          account.changePassword(passwordHash, now);
          account.markEmailVerified(now);
          // Sessions live in the same database, so they end in this transaction.
          sessions
              .findByPrincipalName(account.getId().toString())
              .keySet()
              .forEach(sessions::deleteById);
          loginThrottle.clear(Tokens.hash(account.getEmail()));
          log.atInfo().addKeyValue("accountId", account.getId()).log("Password reset");
        });
  }

  private void issueReset(AccountEntity account) {
    Instant now = clock.instant();
    tokens.deleteUnused(account.getId(), TokenType.PASSWORD_RESET);
    String token = Tokens.newToken();
    VerificationTokenEntity entity =
        tokens.save(
            new VerificationTokenEntity(
                ids.next(), account.getId(), TokenType.PASSWORD_RESET, Tokens.hash(token), now));
    events.publishEvent(
        new PasswordResetRequested(
            account.getId(), account.getEmail(), entity.getId(), token, entity.getExpiresAt()));
    log.atInfo().addKeyValue("accountId", account.getId()).log("Password reset requested");
  }

  private static ApiException invalidToken() {
    return new ApiException(
        ErrorCode.TOKEN_INVALID, "This link has expired or was already used. Ask for a new one.");
  }
}
