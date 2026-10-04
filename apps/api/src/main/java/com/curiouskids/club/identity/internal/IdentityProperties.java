package com.curiouskids.club.identity.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Identity configuration under {@code club.identity.*}.
 *
 * @param consentVersion version of the terms and privacy notice that registrants accept (BR-28);
 *     bump it when the legal pages change (T-061)
 * @param requestLimits hourly request limits (security-and-privacy.md)
 */
@Validated
@ConfigurationProperties("club.identity")
public record IdentityProperties(
    @NotBlank @DefaultValue("2026-10") String consentVersion,
    @Valid @DefaultValue RequestLimits requestLimits) {

  /**
   * Hourly limits from security-and-privacy.md. Only the local profile raises the per-address ones,
   * because every local request comes from one address.
   *
   * @param registerPerAddress registrations per client address
   * @param emailLinksPerEmail reset and verification emails together per email address
   * @param emailLinksPerAddress reset and verification emails together per client address
   */
  public record RequestLimits(
      @Positive @DefaultValue("10") int registerPerAddress,
      @Positive @DefaultValue("3") int emailLinksPerEmail,
      @Positive @DefaultValue("20") int emailLinksPerAddress) {}
}
