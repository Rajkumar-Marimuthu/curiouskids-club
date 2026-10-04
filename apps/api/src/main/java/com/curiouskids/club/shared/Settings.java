package com.curiouskids.club.shared;

import java.util.List;

/**
 * Business rule settings from the {@code setting} table (see {@code business-rules.md}). Values are
 * cached and reloaded after {@link #invalidate()} or when the cache is older than a minute, so a
 * change made on one API instance reaches the others without a restart.
 */
public interface Settings {

  /** The raw value. Throws {@link IllegalStateException} for an unknown key. */
  String get(String key);

  int getInt(String key);

  /** A comma-separated list of integers, for example {@code reminder.overdue-days}. */
  List<Integer> getIntList(String key);

  /** Drop the cache so the next read loads the table again. */
  void invalidate();
}
