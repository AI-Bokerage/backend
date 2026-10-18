package io.genfin.money.internal.format;

import io.genfin.money.format.FormattingContext;
import io.genfin.money.money.Money;
import io.genfin.money.port.format.MoneyFormatter;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public final class DefaultMoneyFormatter implements MoneyFormatter {

  @Override
  public String format(Money money, FormattingContext context) {
    DecimalFormat decimalFormat = numberFormat(money, context);
    BigDecimal amount = money.amount();
    String number = decimalFormat.format(amount.abs());
    String code = money.currency().code();
    String symbol = money.currency().symbol();

    String body =
        switch (context.style()) {
          case SYMBOL_FIRST -> symbol + number;
          case SYMBOL_LAST -> number + symbol;
          case ISO_CODE -> code + " " + number;
          case ACCOUNTING -> number + " " + code;
        };

    boolean negative = amount.signum() < 0;
    return switch (context.style()) {
      case ACCOUNTING -> negative ? "(" + body + ")" : body;
      default -> negative ? "-" + body : body;
    };
  }

  private DecimalFormat numberFormat(Money money, FormattingContext context) {
    DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(context.locale());
    int fractionDigits = money.currency().fractionDigits();
    StringBuilder pattern = new StringBuilder("#,##0");
    if (fractionDigits > 0) {
      pattern.append('.').append("0".repeat(fractionDigits));
    }
    return new DecimalFormat(pattern.toString(), symbols);
  }
}
