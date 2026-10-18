package io.genfin.payment.failure;

public enum RetryRecommendation {
  RETRY_IMMEDIATELY,
  RETRY_WITH_BACKOFF,
  RETRY_WITH_DIFFERENT_METHOD,
  REQUIRES_USER_ACTION,
  DO_NOT_RETRY
}
