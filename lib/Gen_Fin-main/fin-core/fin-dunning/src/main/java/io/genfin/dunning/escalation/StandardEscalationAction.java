package io.genfin.dunning.escalation;

import io.genfin.api.validation.Validate;

/**
 * A small convenience set of illustrative escalation actions. Not exhaustive and purely
 * illustrative examples, never executed by fin-dunning itself - an application is free to {@link
 * #of(String)} any action code its own operational workflows actually perform (notify a manager,
 * suspend a service, freeze an account, write off the obligation, route it to manual review, or
 * anything else it supports).
 */
public record StandardEscalationAction(String code) implements EscalationAction {

  public StandardEscalationAction {
    Validate.notBlank(code, "code must not be blank.");
  }

  public static StandardEscalationAction of(String code) {
    return new StandardEscalationAction(code);
  }

  public static final StandardEscalationAction NOTIFY_MANAGER = of("NOTIFY_MANAGER");
  public static final StandardEscalationAction SUSPEND_SERVICE = of("SUSPEND_SERVICE");
  public static final StandardEscalationAction FREEZE_ACCOUNT = of("FREEZE_ACCOUNT");
  public static final StandardEscalationAction WRITE_OFF = of("WRITE_OFF");
  public static final StandardEscalationAction MANUAL_REVIEW = of("MANUAL_REVIEW");
}
