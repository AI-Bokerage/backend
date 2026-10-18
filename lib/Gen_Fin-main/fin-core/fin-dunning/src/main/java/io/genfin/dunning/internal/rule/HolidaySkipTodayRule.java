package io.genfin.dunning.internal.rule;

import io.genfin.dunning.port.rule.CollectionRule;
import io.genfin.dunning.rule.CollectionDecision;
import io.genfin.dunning.rule.CollectionRuleContext;
import io.genfin.dunning.rule.StandardCollectionOutcome;
import java.time.LocalDate;
import java.util.List;

/**
 * Illustrative example rule: fires when {@code asOf} falls on a day the resolved {@code
 * RetryPolicy}'s {@code BusinessCalendar} does not consider a business day - whatever weekend rule
 * and holiday calendar that calendar was configured with, never a fixed weekend/holiday list of its
 * own. Stays silent when the context carries no retry policy (and therefore no calendar/zone) to
 * check against.
 */
public final class HolidaySkipTodayRule implements CollectionRule {

  public static final String CODE = "HOLIDAY_SKIP_TODAY";

  @Override
  public List<CollectionDecision> evaluate(CollectionRuleContext context) {
    if (context.retryPolicy() == null) {
      return List.of();
    }
    LocalDate today = context.asOf().atZone(context.retryPolicy().zone()).toLocalDate();
    if (context.retryPolicy().businessCalendar().isBusinessDay(today)) {
      return List.of();
    }
    return List.of(
        CollectionDecision.of(
            CODE,
            StandardCollectionOutcome.SKIP,
            "Today (" + today + ") is not a business day per the resolved BusinessCalendar."));
  }
}
