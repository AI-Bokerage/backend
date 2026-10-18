package io.genfin.document.api.exception;

import io.genfin.api.exception.GenFinException;
import io.genfin.document.api.identity.TemplateId;
import java.util.Map;

public final class TemplateNotFoundException extends GenFinException {

  public TemplateNotFoundException(TemplateId templateId) {
    super(
        DocumentErrorCode.TEMPLATE_NOT_FOUND,
        "No template registered for id: " + templateId,
        null,
        Map.of("templateId", templateId));
  }
}
