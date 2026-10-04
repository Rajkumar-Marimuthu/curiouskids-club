package com.curiouskids.club.shared.internal;

import com.curiouskids.club.shared.Settings;
import com.curiouskids.club.shared.internal.persistence.SettingEntity;
import com.curiouskids.club.shared.internal.persistence.SettingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
class DbSettings implements Settings {

  static final Duration CACHE_TTL = Duration.ofMinutes(1);

  private record Snapshot(Map<String, String> values, Instant loadedAt) {}

  private final SettingRepository repository;
  private final Clock clock;
  private volatile Snapshot snapshot;

  DbSettings(SettingRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  public String get(String key) {
    String value = current().values().get(key);
    if (value == null) {
      throw new IllegalStateException("Unknown setting: " + key);
    }
    return value;
  }

  @Override
  public int getInt(String key) {
    return parseInt(key, get(key));
  }

  @Override
  public List<Integer> getIntList(String key) {
    return Arrays.stream(get(key).split(",")).map(v -> parseInt(key, v.trim())).toList();
  }

  @Override
  public void invalidate() {
    snapshot = null;
  }

  private Snapshot current() {
    Snapshot s = snapshot;
    Instant now = clock.instant();
    if (s == null || !now.isBefore(s.loadedAt().plus(CACHE_TTL))) {
      s = load(now);
      snapshot = s;
    }
    return s;
  }

  private Snapshot load(Instant now) {
    Map<String, String> values =
        repository.findAll().stream()
            .collect(Collectors.toUnmodifiableMap(SettingEntity::getKey, SettingEntity::getValue));
    return new Snapshot(values, now);
  }

  private static int parseInt(String key, String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new IllegalStateException("Setting " + key + " is not an integer: " + value, e);
    }
  }
}
