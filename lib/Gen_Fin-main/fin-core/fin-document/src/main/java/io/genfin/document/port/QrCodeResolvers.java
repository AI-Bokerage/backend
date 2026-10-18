package io.genfin.document.port;

import io.genfin.document.internal.qr.DefaultQrCodeRegistry;
import io.genfin.document.internal.qr.DefaultQrCodeResolver;

/**
 * Factory for a standard, ready-to-use {@link QrCodeResolver} plus a handle to register {@link
 * QrCodeProvider}s against it. This is the only consumer-reachable way to obtain a working {@link
 * QrCodeResolver}: the concrete registry/resolver implementations all live in internal packages
 * that the module never exports.
 */
public final class QrCodeResolvers {

  private QrCodeResolvers() {}

  public static Bundle standard() {
    DefaultQrCodeRegistry registry = new DefaultQrCodeRegistry();
    DefaultQrCodeResolver resolver = new DefaultQrCodeResolver(registry);
    return new Bundle() {
      @Override
      public void register(QrCodeProvider provider) {
        registry.register(provider);
      }

      @Override
      public QrCodeResolver resolver() {
        return resolver;
      }
    };
  }

  /** Registration handle and working resolver, wired together against the same backing registry. */
  public interface Bundle {
    void register(QrCodeProvider provider);

    QrCodeResolver resolver();
  }
}
