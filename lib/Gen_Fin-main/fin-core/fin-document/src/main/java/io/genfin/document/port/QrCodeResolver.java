package io.genfin.document.port;

import io.genfin.document.api.qr.QrCodeContent;

public interface QrCodeResolver {
  QrCodeContent resolve(String key);
}
