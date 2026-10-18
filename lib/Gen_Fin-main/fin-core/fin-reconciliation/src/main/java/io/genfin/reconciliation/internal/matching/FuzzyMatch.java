package io.genfin.reconciliation.internal.matching;

import io.genfin.reconciliation.matching.MatchCandidate;
import io.genfin.reconciliation.matching.MatchResult;
import io.genfin.reconciliation.matching.MatchingContext;
import io.genfin.reconciliation.port.matching.MatchingStrategy;
import java.util.Locale;

/**
 * Matches on reference text similarity rather than equality — useful for bank statement narrations
 * that mangle an otherwise-identical reference (case, punctuation, truncation). Similarity is a
 * normalized Levenshtein distance; a result at or above {@link MatchingContext#fuzzyThreshold()} is
 * a match, exact text is always a full-confidence match.
 */
public final class FuzzyMatch implements MatchingStrategy {

  private static final double FULL_CONFIDENCE = 1.0;

  @Override
  public MatchResult match(MatchCandidate left, MatchCandidate right, MatchingContext context) {
    String leftValue = normalize(left.reference().value().value());
    String rightValue = normalize(right.reference().value().value());
    if (leftValue.isEmpty() || rightValue.isEmpty()) {
      return MatchResult.notMatched(left.id(), name());
    }
    double similarity = similarity(leftValue, rightValue);
    if (similarity >= FULL_CONFIDENCE) {
      return MatchResult.matched(left.id(), right.id(), name());
    }
    if (similarity >= context.fuzzyThreshold()) {
      return MatchResult.partiallyMatched(
          left.id(), right.id(), similarity, null, name(), "Reference similarity " + similarity);
    }
    return MatchResult.notMatched(left.id(), name());
  }

  private String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
  }

  private double similarity(String left, String right) {
    int distance = levenshtein(left, right);
    int longest = Math.max(left.length(), right.length());
    return longest == 0 ? 1.0 : 1.0 - ((double) distance / longest);
  }

  private int levenshtein(String left, String right) {
    int[] previous = new int[right.length() + 1];
    int[] current = new int[right.length() + 1];
    for (int j = 0; j <= right.length(); j++) {
      previous[j] = j;
    }
    for (int i = 1; i <= left.length(); i++) {
      current[0] = i;
      for (int j = 1; j <= right.length(); j++) {
        int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
        current[j] =
            Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
      }
      System.arraycopy(current, 0, previous, 0, current.length);
    }
    return previous[right.length()];
  }

  private String name() {
    return "FuzzyMatch";
  }
}
