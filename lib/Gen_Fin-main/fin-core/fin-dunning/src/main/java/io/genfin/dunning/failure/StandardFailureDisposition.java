package io.genfin.dunning.failure;

import io.genfin.api.validation.Validate;

/**
 * A small convenience set of illustrative dispositions. Not exhaustive and purely illustrative,
 * never executed by fin-dunning itself - an application is free to {@link #of(String)} any
 * disposition code its own operational workflows actually perform.
 */
public record StandardFailureDisposition(String code) implements FailureDisposition {

  public StandardFailureDisposition {
    Validate.notBlank(code, "code must not be blank.");
  }

  public static StandardFailureDisposition of(String code) {
    return new StandardFailureDisposition(code);
  }

  public static final StandardFailureDisposition RETRY = of("RETRY");
  public static final StandardFailureDisposition ESCALATE = of("ESCALATE");
  public static final StandardFailureDisposition WRITE_OFF = of("WRITE_OFF");
  public static final StandardFailureDisposition MANUAL_REVIEW = of("MANUAL_REVIEW");
  public static final StandardFailureDisposition NO_ACTION = of("NO_ACTION");
}
