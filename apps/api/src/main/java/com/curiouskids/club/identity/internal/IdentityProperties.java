package com.curiouskids.club.identity.internal;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Identity configuration under {@code club.identity.*}.
 *
 * @param consentVersion version of the terms and privacy notice that registrants accept (BR-28);
 *     bump it when the legal pages change (T-061)
 */
@Validated
@ConfigurationProperties("club.identity")
public record IdentityProperties(@NotBlank @DefaultValue("2026-10") String consentVersion) {}
