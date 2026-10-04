package com.curiouskids.club.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.curiouskids.club.identity.CurrentUser;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Creates accounts straight in the database for tests that need someone to log in as. */
@Component
public class TestAccounts {

  public static final String PASSWORD = "purple giraffes read slowly";

  private final JdbcClient jdbc;
  private final String passwordHash;

  TestAccounts(JdbcClient jdbc, PasswordEncoder encoder) {
    this.jdbc = jdbc;
    this.passwordHash = encoder.encode(PASSWORD);
  }

  /** A created account and its login email. */
  public record Account(UUID id, String email, UUID familyId, String role) {

    /** Sends the request as this account, with the principal a real login would create. */
    public RequestPostProcessor session() {
      CurrentUser user = new CurrentUser(id, role, familyId);
      return authentication(
          UsernamePasswordAuthenticationToken.authenticated(
              user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
  }

  public Account member(boolean verified) {
    UUID familyId = UUID.randomUUID();
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.sql(
            "insert into family (id, display_name, created_at, updated_at)"
                + " values (:id, 'Test Family', :now, :now)")
        .param("id", familyId)
        .param("now", now)
        .update();
    return insert("MEMBER", familyId, verified);
  }

  public Account volunteer() {
    return insert("VOLUNTEER", null, true);
  }

  public Account admin() {
    return insert("ADMIN", null, true);
  }

  public void disable(Account account) {
    jdbc.sql("update account set status = 'DISABLED' where id = :id")
        .param("id", account.id())
        .update();
  }

  private Account insert(String role, UUID familyId, boolean verified) {
    UUID id = UUID.randomUUID();
    String email = role.toLowerCase() + "-" + id + "@example.com";
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.sql(
            """
            insert into account (id, email, password_hash, role, family_id, email_verified_at,
                                 consent_version, consent_at, created_at, updated_at)
            values (:id, :email, :hash, :role, :familyId, :verifiedAt,
                    :consentVersion, :consentAt, :now, :now)
            """)
        .param("id", id)
        .param("email", email)
        .param("hash", passwordHash)
        .param("role", role)
        .param("familyId", familyId)
        .param("verifiedAt", verified ? now : null)
        .param("consentVersion", familyId == null ? null : "2026-10")
        .param("consentAt", familyId == null ? null : now)
        .param("now", now)
        .update();
    return new Account(id, email, familyId, role);
  }
}
