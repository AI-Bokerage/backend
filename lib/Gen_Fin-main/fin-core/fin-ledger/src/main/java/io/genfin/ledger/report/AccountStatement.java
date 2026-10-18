package io.genfin.ledger.report;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.util.CollectionUtils;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.AccountingPeriodId;
import io.genfin.money.money.Money;
import java.time.Instant;
import java.util.List;

/**
 * A reportable view of a single {@link AccountId}'s activity over one {@link
 * io.genfin.ledger.period.AccountingPeriod}: its opening balance, every activity line in between,
 * and the resulting closing balance. Rendering to a concrete format is the job of a {@link
 * io.genfin.ledger.port.report.ReportFormatter} - this type only holds the data.
 */
public record AccountStatement(
    AccountId accountId,
    AccountingPeriodId periodId,
    Money openingBalance,
    List<ReportEntry> lines,
    Money closingBalance,
    Instant generatedAt)
    implements ValueObject {

  public AccountStatement {
    Validate.notNull(accountId, "accountId must not be null.");
    Validate.notNull(periodId, "periodId must not be null.");
    Validate.notNull(openingBalance, "openingBalance must not be null.");
    lines = CollectionUtils.immutableList(lines);
    Validate.notNull(closingBalance, "closingBalance must not be null.");
    Validate.notNull(generatedAt, "generatedAt must not be null.");
  }

  public static AccountStatement of(
      AccountId accountId,
      AccountingPeriodId periodId,
      Money openingBalance,
      List<ReportEntry> lines,
      Money closingBalance,
      Instant generatedAt) {
    return new AccountStatement(
        accountId, periodId, openingBalance, lines, closingBalance, generatedAt);
  }
}
