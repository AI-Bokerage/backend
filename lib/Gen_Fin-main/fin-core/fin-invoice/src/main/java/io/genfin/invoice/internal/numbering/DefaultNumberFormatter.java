package io.genfin.invoice.internal.numbering;

import io.genfin.invoice.numbering.NumberTemplate;
import io.genfin.invoice.port.numbering.NumberFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Substitutes {@code {TOKEN}} placeholders in a {@link NumberTemplate}'s pattern with the supplied
 * token values.
 */
public final class DefaultNumberFormatter implements NumberFormatter {

  private static final Pattern TOKEN = Pattern.compile("\\{(\\w+)}");

  @Override
  public String format(NumberTemplate template, Map<String, String> tokens) {
    Matcher matcher = TOKEN.matcher(template.pattern());
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      String value = tokens.getOrDefault(matcher.group(1), matcher.group());
      matcher.appendReplacement(result, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(result);
    return result.toString();
  }
}
