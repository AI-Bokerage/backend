package io.genfin.document.api.exception;

import io.genfin.api.exception.GenFinException;
import io.genfin.document.api.identity.LetterheadId;
import java.util.Map;

public final class LetterheadNotFoundException extends GenFinException {

  public LetterheadNotFoundException(LetterheadId letterheadId) {
    super(
        DocumentErrorCode.LETTERHEAD_NOT_FOUND,
        "No letterhead provider supports id: " + letterheadId,
        null,
        Map.of("letterheadId", letterheadId));
  }
}
