package io.genfin.document.internal.qr;

import io.genfin.document.api.exception.QrCodeNotFoundException;
import io.genfin.document.api.qr.QrCodeContent;
import io.genfin.document.port.QrCodeProvider;
import io.genfin.document.port.QrCodeResolver;

public final class DefaultQrCodeResolver implements QrCodeResolver {

  private final DefaultQrCodeRegistry registry;

  public DefaultQrCodeResolver(DefaultQrCodeRegistry registry) {
    this.registry = registry;
  }

  @Override
  public QrCodeContent resolve(String key) {
    for (QrCodeProvider provider : registry.providers()) {
      if (provider.supports(key)) {
        return provider.resolve(key);
      }
    }
    throw new QrCodeNotFoundException(key);
  }
}
