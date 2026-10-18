package io.genfin.dunning.config;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.calendar.BusinessCalendars;
import io.genfin.dunning.port.calendar.BusinessCalendar;

/**
 * Immutable, builder-based configuration for the Business Calendar concern: the {@link
 * BusinessCalendar} an application resolves for calendar-aware scheduling. The
 * no-holidays/Sat-Sun-weekend default below exists only so {@code
 * CalendarConfiguration.builder().build()} compiles and is testable out of the box - a real
 * deployment supplies its own {@code HolidayProvider}/{@code WeekendStrategy} via {@code
 * CalendarPolicy}, never these literals.
 */
public final class CalendarConfiguration {

  private final BusinessCalendar businessCalendar;

  private CalendarConfiguration(Builder builder) {
    this.businessCalendar =
        Validate.notNull(builder.businessCalendar, "businessCalendar must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public BusinessCalendar businessCalendar() {
    return businessCalendar;
  }

  public static final class Builder {

    private BusinessCalendar businessCalendar = BusinessCalendars.standard();

    public Builder businessCalendar(BusinessCalendar businessCalendar) {
      this.businessCalendar = businessCalendar;
      return this;
    }

    public CalendarConfiguration build() {
      return new CalendarConfiguration(this);
    }
  }
}
