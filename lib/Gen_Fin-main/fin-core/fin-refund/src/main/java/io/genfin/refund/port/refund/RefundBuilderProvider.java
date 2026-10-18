package io.genfin.refund.port.refund;

import io.genfin.api.port.spi.Extension;
import io.genfin.refund.refund.RefundBuilder;

/**
 * Supplies a fresh {@link RefundBuilder} per call. The extension point exists so a deployment can
 * swap in a builder pre-populated with deployment-specific defaults (e.g. audit metadata) without
 * every call site knowing about it.
 */
public interface RefundBuilderProvider extends Extension {

  RefundBuilder newBuilder();
}
