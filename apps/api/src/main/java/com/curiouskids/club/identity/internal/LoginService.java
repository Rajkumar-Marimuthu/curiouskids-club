package com.curiouskids.club.identity.internal;

import com.curiouskids.club.identity.CurrentUser;
import com.curiouskids.club.identity.internal.persistence.AccountEntity;
import com.curiouskids.club.identity.internal.persistence.AccountRepository;
import com.curiouskids.club.identity.internal.persistence.AccountStatus;
import com.curiouskids.club.shared.ApiException;
import com.curiouskids.club.shared.ErrorCode;
import com.curiouskids.club.shared.RateLimitedException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks credentials (FR-ID-03). Every failure gets the same message, whether the email is unknown,
 * the password wrong or the account disabled, and takes similar time. Logs carry account IDs only.
 */
@Service
public class LoginService {

  private static final Logger log = LoggerFactory.getLogger(LoginService.class);

  private final AccountRepository accounts;
  private final PasswordEncoder passwordEncoder;
  private final LoginThrottle throttle;
  // Compared against when the email is unknown, so the response takes as long as a real check.
  private final String dummyHash;

  LoginService(
      AccountRepository accounts, PasswordEncoder passwordEncoder, LoginThrottle throttle) {
    this.accounts = accounts;
    this.passwordEncoder = passwordEncoder;
    this.throttle = throttle;
    this.dummyHash = passwordEncoder.encode(Tokens.newToken());
  }

  /** The logged-in account and whether its email is verified. */
  public record Me(CurrentUser user, boolean emailVerified) {}

  /**
   * Returns the account if the email and password match an active account.
   *
   * @param clientAddress the caller's network address; only its hash is stored
   * @throws RateLimitedException after too many failures for this email or address
   * @throws ApiException UNAUTHENTICATED for any other failure
   */
  public Me login(String rawEmail, String password, String clientAddress) {
    String email = RegistrationService.normalise(rawEmail);
    String emailHash = Tokens.hash(email);
    String ipHash = Tokens.hash(clientAddress);
    Optional<Duration> wait = throttle.blockedFor(emailHash, ipHash);
    if (wait.isPresent()) {
      log.atWarn().addKeyValue("retryAfterSeconds", wait.get().toSeconds()).log("Login blocked");
      throw new RateLimitedException(
          "Too many attempts. Please wait a few minutes and try again.", wait.get());
    }
    Optional<AccountEntity> account = accounts.findByEmail(email);
    boolean matches =
        passwordEncoder.matches(
            password, account.map(AccountEntity::getPasswordHash).orElse(dummyHash));
    if (account.isEmpty() || !matches || account.get().getStatus() != AccountStatus.ACTIVE) {
      throttle.recordFailure(emailHash, ipHash);
      log.info("Login failed");
      throw new ApiException(ErrorCode.UNAUTHENTICATED, "Email or password is incorrect.");
    }
    throttle.clear(emailHash);
    log.atInfo().addKeyValue("accountId", account.get().getId()).log("Logged in");
    return me(account.get());
  }

  /** The current state of a logged-in account. */
  @Transactional(readOnly = true)
  public Me me(UUID accountId) {
    return accounts
        .findById(accountId)
        .map(LoginService::me)
        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Please log in."));
  }

  private static Me me(AccountEntity account) {
    return new Me(
        new CurrentUser(account.getId(), account.getRole().name(), account.getFamilyId()),
        account.isEmailVerified());
  }
}
