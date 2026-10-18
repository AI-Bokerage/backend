package io.genfin.dunning.calendar;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.port.calendar.HolidayProvider;
import io.genfin.dunning.port.calendar.WeekendStrategy;

/**
 * The full, explicit policy governing how a {@code BusinessCalendar} treats a date: which days of
 * the week are weekends, which application-supplied holidays apply, whether scheduling is
 * business-calendar-aware at all (vs pure calendar-day, where every day - including weekends and
 * holidays - is eligible), and which {@link RollConvention} adjusts a non-business day onto one.
 *
 * <p>Every builder default below exists only so {@code CalendarPolicy.builder().build()} compiles
 * and is testable out of the box - resolving the real policy for a deployment always flows through
 * an application's own {@code DunningPolicy}, never these literals.
 */
public final class CalendarPolicy {

  private final WeekendStrategy weekendStrategy;
  private final HolidayProvider holidayProvider;
  private final boolean businessCalendarAware;
  private final RollConvention rollConvention;

  private CalendarPolicy(Builder builder) {
    this.weekendStrategy =
        Validate.notNull(builder.weekendStrategy, "weekendStrategy must not be null.");
    this.holidayProvider =
        Validate.notNull(builder.holidayProvider, "holidayProvider must not be null.");
    this.businessCalendarAware = builder.businessCalendarAware;
    this.rollConvention =
        Validate.notNull(builder.rollConvention, "rollConvention must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public WeekendStrategy weekendStrategy() {
    return weekendStrategy;
  }

  public HolidayProvider holidayProvider() {
    return holidayProvider;
  }

  /**
   * False means pure calendar-day scheduling: every day is eligible, weekends/holidays included.
   */
  public boolean businessCalendarAware() {
    return businessCalendarAware;
  }

  public RollConvention rollConvention() {
    return rollConvention;
  }

  public static final class Builder {

    private WeekendStrategy weekendStrategy = WeekendStrategies.satSun();
    private HolidayProvider holidayProvider = HolidayProviders.none();
    private boolean businessCalendarAware = true;
    private RollConvention rollConvention = RollConventions.FORWARD;

    public Builder weekendStrategy(WeekendStrategy weekendStrategy) {
      this.weekendStrategy = weekendStrategy;
      return this;
    }

    public Builder holidayProvider(HolidayProvider holidayProvider) {
      this.holidayProvider = holidayProvider;
      return this;
    }

    public Builder businessCalendarAware(boolean businessCalendarAware) {
      this.businessCalendarAware = businessCalendarAware;
      return this;
    }

    public Builder rollConvention(RollConvention rollConvention) {
      this.rollConvention = rollConvention;
      return this;
    }

    public CalendarPolicy build() {
      return new CalendarPolicy(this);
    }
  }
}
