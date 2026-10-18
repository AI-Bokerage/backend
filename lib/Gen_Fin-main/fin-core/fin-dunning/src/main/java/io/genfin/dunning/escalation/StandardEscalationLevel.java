package io.genfin.dunning.escalation;

import io.genfin.api.validation.Validate;

/**
 * A small convenience set of escalation levels. Not exhaustive and purely illustrative -
 * fin-dunning is escalation-ladder-agnostic, so applications are free to {@link #of(String)} any
 * level code their own escalation policy actually uses.
 */
public record StandardEscalationLevel(String code) implements EscalationLevel {

  public StandardEscalationLevel {
    Validate.notBlank(code, "code must not be blank.");
  }

  public static StandardEscalationLevel of(String code) {
    return new StandardEscalationLevel(code);
  }

  public static final StandardEscalationLevel LEVEL_1 = of("LEVEL_1");
  public static final StandardEscalationLevel LEVEL_2 = of("LEVEL_2");
  public static final StandardEscalationLevel LEVEL_3 = of("LEVEL_3");
}
