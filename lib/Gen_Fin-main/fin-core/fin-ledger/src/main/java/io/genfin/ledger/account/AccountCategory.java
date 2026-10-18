package io.genfin.ledger.account;

/**
 * An application-defined grouping of accounts within a {@link AccountClassification} (e.g. a
 * "current" vs. "fixed" split of ASSET accounts). Not a closed enum - implement this interface to
 * add whatever categories a deployment needs, the same way {@code
 * io.genfin.refund.reason.RefundReason} is extended by callers. Gen-Fin ships no built-in
 * categories; any concrete category is an application concern.
 */
public interface AccountCategory {

  String code();
}
