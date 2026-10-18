package io.genfin.dunning.reminder;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * An opaque pointer to whatever template the consuming application owns (an email template id, an
 * SMS copy key, a push notification template, ...). fin-dunning never resolves, renders, or stores
 * the actual template content behind this reference - it only plans that a reminder should carry
 * one, and hands the reference back so the application can render and send.
 */
public record ReminderTemplateReference(String templateId) implements ValueObject {

  public ReminderTemplateReference {
    Validate.notBlank(templateId, "templateId must not be blank.");
  }

  public static ReminderTemplateReference of(String templateId) {
    return new ReminderTemplateReference(templateId);
  }
}
