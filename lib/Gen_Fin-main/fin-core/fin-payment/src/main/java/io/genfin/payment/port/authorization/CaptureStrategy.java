package io.genfin.payment.port.authorization;

import io.genfin.api.port.spi.Extension;

/**
 * Whether a captured authorization should be captured immediately (automatic) or await an explicit
 * call (manual).
 */
public interface CaptureStrategy extends Extension {

  boolean isAutomaticCapture();
}
