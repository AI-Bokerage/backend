package io.genfin.dunning.support;

import io.genfin.dunning.obligation.ObligationType;

public final class TestObligationTypes {

  public static final ObligationType INVOICE = () -> "INVOICE";
  public static final ObligationType LOAN_INSTALLMENT = () -> "LOAN_INSTALLMENT";

  private TestObligationTypes() {}
}
