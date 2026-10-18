package io.genfin.dunning.obligation;

/**
 * The kind of a {@link FinancialObligation} (what an application might call an invoice line, a
 * subscription renewal, a loan installment, an EMI, a marketplace settlement, or a vendor payment).
 * Not a closed enum - Gen-Fin defines no business obligation types of its own; every consuming
 * application implements this interface for its own domain, the same extensible-taxonomy pattern as
 * {@code io.genfin.ledger.account.AccountCategory}.
 */
public interface ObligationType {

  String code();
}
