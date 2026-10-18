package io.genfin.document.port;

import io.genfin.document.api.identity.LetterheadId;
import io.genfin.document.api.letterhead.Letterhead;

public interface LetterheadResolver {
  Letterhead resolve(LetterheadId id);
}
