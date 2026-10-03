package com.curiouskids.club.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.curiouskids.club.shared.SettingKeys;
import com.curiouskids.club.shared.Settings;
import com.curiouskids.club.shared.internal.persistence.SettingRepository;
import com.curiouskids.club.support.IntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class SettingsTest extends IntegrationTest {

  private static final String KEY = SettingKeys.LOAN_PERIOD_DAYS;

  @Autowired Settings settings;
  @Autowired SettingRepository repository;
  @Autowired JdbcClient jdbc;

  @AfterEach
  void restoreDefault() {
    setValue(KEY, "14");
    settings.invalidate();
  }

  @Test
  @DisplayName("NFR-11: default business rule settings are seeded by the baseline migration")
  void defaultsSeeded() {
    assertThat(settings.getInt(SettingKeys.SLOT_DEFAULT_CAPACITY)).isEqualTo(10);
    assertThat(settings.getInt(SettingKeys.SLOT_BOOKING_HORIZON_DAYS)).isEqualTo(14);
    assertThat(settings.getInt(SettingKeys.SLOT_CUTOFF_MINUTES)).isEqualTo(120);
    assertThat(settings.getInt(SettingKeys.LOAN_PERIOD_DAYS)).isEqualTo(14);
    assertThat(settings.getInt(SettingKeys.LOAN_MAX_RENEWALS)).isEqualTo(1);
    assertThat(settings.getInt(SettingKeys.LOAN_POSSIBLE_LOST_AFTER_DAYS)).isEqualTo(30);
    assertThat(settings.getInt(SettingKeys.LIMITS_MAX_ACTIVE_ITEMS)).isEqualTo(5);
    assertThat(settings.getInt(SettingKeys.LIMITS_MAX_WAITLIST_ENTRIES)).isEqualTo(5);
    assertThat(settings.getInt(SettingKeys.LIMITS_MAX_CHILDREN)).isEqualTo(6);
    assertThat(settings.getInt(SettingKeys.PROMOTION_CHOOSE_SLOT_HOURS)).isEqualTo(48);
    assertThat(settings.getInt(SettingKeys.NOSHOW_FLAG_COUNT)).isEqualTo(3);
    assertThat(settings.getInt(SettingKeys.NOSHOW_FLAG_DAYS)).isEqualTo(60);
    assertThat(settings.getInt(SettingKeys.REMINDER_PICKUP_HOURS_BEFORE)).isEqualTo(24);
    assertThat(settings.getInt(SettingKeys.REMINDER_DUE_SOON_DAYS)).isEqualTo(2);
    assertThat(settings.getIntList(SettingKeys.REMINDER_OVERDUE_DAYS)).containsExactly(1, 4, 8);
    assertThat(settings.getInt(SettingKeys.REMINDER_AWAITING_SLOT_HOURS_BEFORE_EXPIRY))
        .isEqualTo(24);
    assertThat(settings.getInt(SettingKeys.RETENTION_INACTIVE_MONTHS)).isEqualTo(24);
  }

  @Test
  @DisplayName("Unknown or non-numeric settings fail loudly")
  void invalidReads() {
    assertThatThrownBy(() -> settings.get("no.such.key"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no.such.key");

    setValue(KEY, "fourteen");
    settings.invalidate();
    assertThatThrownBy(() -> settings.getInt(KEY)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("NFR-03: cached values refresh after invalidate or after the cache TTL")
  void cacheRefreshes() {
    MutableClock clock = new MutableClock(Instant.parse("2026-09-21T16:00:00Z"));
    DbSettings cached = new DbSettings(repository, clock);
    assertThat(cached.getInt(KEY)).isEqualTo(14);

    setValue(KEY, "21");
    assertThat(cached.getInt(KEY)).as("still cached").isEqualTo(14);

    clock.advance(DbSettings.CACHE_TTL);
    assertThat(cached.getInt(KEY)).as("reloaded after TTL").isEqualTo(21);

    setValue(KEY, "28");
    cached.invalidate();
    assertThat(cached.getInt(KEY)).as("reloaded after invalidate").isEqualTo(28);
  }

  private void setValue(String key, String value) {
    jdbc.sql("update setting set value = :value where key = :key")
        .param("value", value)
        .param("key", key)
        .update();
  }

  private static final class MutableClock extends Clock {

    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(java.time.Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
