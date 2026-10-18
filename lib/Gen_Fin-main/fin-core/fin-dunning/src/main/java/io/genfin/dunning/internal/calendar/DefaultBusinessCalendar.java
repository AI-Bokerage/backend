package io.genfin.dunning.internal.calendar;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.calendar.CalendarPolicy;
import io.genfin.dunning.port.calendar.BusinessCalendar;
import io.genfin.dunning.port.calendar.BusinessDayCalculator;
import java.time.LocalDate;

/** Binds a {@link BusinessDayCalculator} to one fixed {@link CalendarPolicy}. */
public final class DefaultBusinessCalendar implements BusinessCalendar {

  private final BusinessDayCalculator calculator;
  private final CalendarPolicy policy;

  public DefaultBusinessCalendar(BusinessDayCalculator calculator, CalendarPolicy policy) {
    this.calculator = Validate.notNull(calculator, "calculator must not be null.");
    this.policy = Validate.notNull(policy, "policy must not be null.");
  }

  @Override
  public CalendarPolicy policy() {
    return policy;
  }

  @Override
  public boolean isBusinessDay(LocalDate date) {
    return calculator.isBusinessDay(date, policy);
  }

  @Override
  public LocalDate nextBusinessDay(LocalDate date) {
    return calculator.nextBusinessDay(date, policy);
  }

  @Override
  public LocalDate previousBusinessDay(LocalDate date) {
    return calculator.previousBusinessDay(date, policy);
  }

  @Override
  public LocalDate addBusinessDays(LocalDate date, int businessDays) {
    return calculator.addBusinessDays(date, businessDays, policy);
  }

  @Override
  public LocalDate roll(LocalDate date) {
    Validate.notNull(date, "date must not be null.");
    if (!policy.businessCalendarAware()) {
      return date;
    }
    return policy
        .rollConvention()
        .roll(date, candidate -> calculator.isBusinessDay(candidate, policy));
  }
}
