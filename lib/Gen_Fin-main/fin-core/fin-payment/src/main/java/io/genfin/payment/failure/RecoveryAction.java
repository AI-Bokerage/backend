package io.genfin.payment.failure;

public enum RecoveryAction {
  UPDATE_PAYMENT_METHOD,
  CONTACT_ISSUING_BANK,
  RETRY_LATER,
  CONTACT_SUPPORT,
  NONE
}
