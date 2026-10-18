package io.genfin.document.api.model;

import io.genfin.api.validation.Validate;

/** A document type identified by a simple code string (e.g. {@code "INVOICE"}). */
public record StandardDocumentType(String code) implements DocumentType {

  public StandardDocumentType {
    Validate.notBlank(code, "DocumentType code must not be blank");
  }

  public static StandardDocumentType of(String code) {
    return new StandardDocumentType(code);
  }

  public static final StandardDocumentType INVOICE = of("INVOICE");
  public static final StandardDocumentType RECEIPT = of("RECEIPT");
  public static final StandardDocumentType REFUND = of("REFUND");
  public static final StandardDocumentType QUOTE = of("QUOTE");
  public static final StandardDocumentType LEDGER_REPORT = of("LEDGER_REPORT");
  public static final StandardDocumentType TRIAL_BALANCE = of("TRIAL_BALANCE");
  public static final StandardDocumentType STATEMENT = of("STATEMENT");
  public static final StandardDocumentType RECONCILIATION_REPORT = of("RECONCILIATION_REPORT");
  public static final StandardDocumentType DUNNING_LETTER = of("DUNNING_LETTER");
}
