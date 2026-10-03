package com.curiouskids.club.shared;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Infrastructure configuration under the {@code club.*} prefix. Business rules that the owner can
 * change live in the {@code setting} table instead (see {@link Settings}).
 *
 * @param timezone the club timezone; slot dates and times use it (BR-01)
 * @param problemTypeBase base URI for the {@code type} of problem+json errors
 */
@Validated
@ConfigurationProperties("club")
public record ClubProperties(
    @NotNull @DefaultValue("Europe/London") ZoneId timezone,
    @NotNull @DefaultValue("https://curiouskids.example/problems/") URI problemTypeBase) {}
