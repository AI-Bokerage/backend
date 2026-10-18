package io.genfin.dunning.escalation;

/**
 * The outcome of one already-performed escalation action, as reported back by the consuming app.
 * Mirrors {@code io.genfin.dunning.retry.RetryResult}'s reporting shape for the escalation side of
 * planning - fin-dunning only ever decides which action applies; it never performs one, so it has
 * no way to observe this outcome itself.
 */
public enum EscalationResult {
  EXECUTED,
  DEFERRED,
  FAILED
}
