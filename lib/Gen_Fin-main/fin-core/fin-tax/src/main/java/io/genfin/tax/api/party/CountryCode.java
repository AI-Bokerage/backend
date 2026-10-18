package io.genfin.tax.api.party;

/**
 * An open, ISO-3166-alpha-2-shaped country code. Not restricted to a fixed enum since a
 * jurisdiction-agnostic engine cannot know every country a future jurisdiction module might need —
 * compare by {@link #code()}, never {@code instanceof}.
 */
public interface CountryCode {

  String code();
}
