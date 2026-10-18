package io.genfin.money.internal.format;

import io.genfin.api.result.Result;
import io.genfin.money.currency.Currency;
import io.genfin.money.exception.MoneyFormatException;
import io.genfin.money.format.FormattingContext;
import io.genfin.money.money.Money;
import io.genfin.money.port.format.MoneyParser;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the canonical {@code CODE amount} / signed-amount forms this module's own formatter
 * produces.
 */
public final class DefaultMoneyParser implements MoneyParser {

  private static final Pattern NUMERIC = Pattern.compile("-?[0-9][0-9,]*(\\.[0-9]+)?");

  @Override
  public Result<Money> parse(String text, Currency currencyHint, FormattingContext context) {
    try {
      String trimmed = text.trim();
      boolean parenNegative = trimmed.startsWith("(") && trimmed.endsWith(")");
      String unwrapped = parenNegative ? trimmed.substring(1, trimmed.length() - 1) : trimmed;

      Matcher matcher = NUMERIC.matcher(unwrapped);
      if (!matcher.find()) {
        throw new MoneyFormatException(text, null);
      }
      BigDecimal amount = new BigDecimal(matcher.group().replace(",", ""));
      if (parenNegative) {
        amount = amount.negate();
      }
      return Result.success(Money.of(amount, currencyHint));
    } catch (RuntimeException e) {
      return Result.failure(
          io.genfin.money.exception.MoneyErrorCode.INVALID_MONEY_FORMAT,
          "Could not parse: " + text,
          e);
    }
  }
}
