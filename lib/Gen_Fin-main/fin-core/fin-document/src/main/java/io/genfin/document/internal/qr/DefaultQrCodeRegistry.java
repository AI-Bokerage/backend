package io.genfin.document.internal.qr;

import io.genfin.document.port.QrCodeProvider;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DefaultQrCodeRegistry {

  private final List<QrCodeProvider> providers = new CopyOnWriteArrayList<>();

  public void register(QrCodeProvider provider) {
    providers.add(provider);
  }

  public List<QrCodeProvider> providers() {
    return List.copyOf(providers);
  }
}
