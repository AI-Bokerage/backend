package io.genfin.refund.request;

/** Lifecycle status of a {@link RefundRequest}, independent of any executed {@code Refund}. */
public enum RefundRequestStatus {
  PENDING,
  APPROVED,
  REJECTED,
  CANCELLED,
  FULFILLED
}
