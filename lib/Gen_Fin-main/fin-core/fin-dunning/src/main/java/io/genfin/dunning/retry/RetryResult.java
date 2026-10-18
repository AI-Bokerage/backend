package io.genfin.dunning.retry;

/** The outcome of one already-executed retry attempt, as reported back by the consuming app. */
public enum RetryResult {
  SUCCEEDED,
  FAILED,
  PENDING
}
