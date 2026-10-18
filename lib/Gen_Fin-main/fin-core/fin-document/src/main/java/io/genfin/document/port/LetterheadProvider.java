package io.genfin.document.port;

import io.genfin.api.port.spi.Extension;
import io.genfin.document.api.identity.LetterheadId;
import io.genfin.document.api.letterhead.Letterhead;

public interface LetterheadProvider extends Extension {
  boolean supports(LetterheadId id);

  Letterhead resolve(LetterheadId id);
}
