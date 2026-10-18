package io.genfin.document.api.exception;

import io.genfin.api.exception.GenFinException;
import java.util.Map;

public final class QrCodeNotFoundException extends GenFinException {

  public QrCodeNotFoundException(String key) {
    super(
        DocumentErrorCode.QR_CODE_NOT_FOUND,
        "No QR code provider supports key: " + key,
        null,
        Map.of("key", key));
  }
}
