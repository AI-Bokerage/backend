package io.genfin.ledger.account;

/**
 * The *kind* of an {@link Account} (e.g. what an application might call "Cash" or "Accounts
 * Receivable") together with the {@link AccountClassification} it rolls up to. Not a closed enum -
 * Gen-Fin defines no business account types of its own; applications implement this interface and
 * register descriptors through {@link io.genfin.ledger.port.account.AccountTypeRegistry}, the same
 * extensible-taxonomy pattern as {@code io.genfin.refund.reason.RefundReason}.
 */
public interface AccountType {

  String code();

  AccountClassification classification();
}
