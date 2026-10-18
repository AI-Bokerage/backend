package io.genfin.dunning.reminder;

import io.genfin.api.validation.Validate;

/**
 * A small convenience set of common reminder channels. Not exhaustive and purely illustrative -
 * fin-dunning is delivery-mechanism-agnostic, so applications are free to {@link #of(String)} any
 * channel code their own notification infrastructure actually sends on (email, SMS, push, in-app,
 * WhatsApp, IVR call, ...).
 */
public record StandardReminderChannel(String code) implements ReminderChannel {

  public StandardReminderChannel {
    Validate.notBlank(code, "code must not be blank.");
  }

  public static StandardReminderChannel of(String code) {
    return new StandardReminderChannel(code);
  }

  public static final StandardReminderChannel EMAIL = of("EMAIL");
  public static final StandardReminderChannel SMS = of("SMS");
  public static final StandardReminderChannel PUSH = of("PUSH");
  public static final StandardReminderChannel IN_APP = of("IN_APP");
}
