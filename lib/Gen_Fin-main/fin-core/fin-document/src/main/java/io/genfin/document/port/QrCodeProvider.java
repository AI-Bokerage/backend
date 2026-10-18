package io.genfin.document.port;

import io.genfin.api.port.spi.Extension;
import io.genfin.document.api.qr.QrCodeContent;

public interface QrCodeProvider extends Extension {
  boolean supports(String key);

  QrCodeContent resolve(String key);
}
