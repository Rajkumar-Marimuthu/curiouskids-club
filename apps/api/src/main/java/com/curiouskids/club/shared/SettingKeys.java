package com.curiouskids.club.shared;

/** Keys of the {@code setting} table, with the business rule each one configures. */
public final class SettingKeys {

  /** BR-03. */
  public static final String SLOT_DEFAULT_CAPACITY = "slot.default-capacity";

  /** BR-05. */
  public static final String SLOT_BOOKING_HORIZON_DAYS = "slot.booking-horizon-days";

  /** BR-06. */
  public static final String SLOT_CUTOFF_MINUTES = "slot.cutoff-minutes";

  /** BR-07. */
  public static final String LOAN_PERIOD_DAYS = "loan.period-days";

  /** BR-09. */
  public static final String LOAN_MAX_RENEWALS = "loan.max-renewals";

  /** BR-11. */
  public static final String LOAN_POSSIBLE_LOST_AFTER_DAYS = "loan.possible-lost-after-days";

  /** BR-12. */
  public static final String LIMITS_MAX_ACTIVE_ITEMS = "limits.max-active-items";

  /** BR-13. */
  public static final String LIMITS_MAX_WAITLIST_ENTRIES = "limits.max-waitlist-entries";

  /** BR-15. */
  public static final String LIMITS_MAX_CHILDREN = "limits.max-children";

  /** BR-19. */
  public static final String PROMOTION_CHOOSE_SLOT_HOURS = "promotion.choose-slot-hours";

  /** BR-21. */
  public static final String NOSHOW_FLAG_COUNT = "noshow.flag-count";

  /** BR-21. */
  public static final String NOSHOW_FLAG_DAYS = "noshow.flag-days";

  /** BR-32. */
  public static final String REMINDER_PICKUP_HOURS_BEFORE = "reminder.pickup-hours-before";

  /** BR-33. */
  public static final String REMINDER_DUE_SOON_DAYS = "reminder.due-soon-days";

  /** BR-34. */
  public static final String REMINDER_OVERDUE_DAYS = "reminder.overdue-days";

  /** BR-35. */
  public static final String REMINDER_AWAITING_SLOT_HOURS_BEFORE_EXPIRY =
      "reminder.awaiting-slot-hours-before-expiry";

  /** BR-37. */
  public static final String RETENTION_INACTIVE_MONTHS = "retention.inactive-months";

  private SettingKeys() {}
}
