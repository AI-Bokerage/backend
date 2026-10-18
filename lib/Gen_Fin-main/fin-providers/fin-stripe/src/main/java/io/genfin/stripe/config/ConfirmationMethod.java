package io.genfin.stripe.config;

/**
 * Mirrors Stripe's own {@code confirmation_method} on a PaymentIntent: whether the client confirms
 * the intent itself ({@code MANUAL}) or Stripe confirms it automatically on creation ({@code
 * AUTOMATIC}). Kept Stripe-specific rather than promoted to {@code fin-provider-api} since no other
 * provider module has an equivalent concept yet.
 */
public enum ConfirmationMethod {
  AUTOMATIC,
  MANUAL
}
