package io.genfin.razorpay.currency;

import java.util.Set;

/**
 * The currency codes a standard Razorpay account may create orders in. Razorpay settles in INR by
 * default; international currency acceptance requires per-account approval and is out of scope
 * here, so this stays a narrow allow-list rather than guessing at accounts we can't see.
 */
public final class RazorpaySupportedCurrencies {

  public static final Set<String> CODES = Set.of("INR");

  private RazorpaySupportedCurrencies() {}
}
