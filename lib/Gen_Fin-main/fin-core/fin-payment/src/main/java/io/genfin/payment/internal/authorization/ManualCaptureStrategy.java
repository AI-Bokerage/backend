package io.genfin.payment.internal.authorization;

import io.genfin.payment.port.authorization.CaptureStrategy;

public final class ManualCaptureStrategy implements CaptureStrategy {

  @Override
  public boolean isAutomaticCapture() {
    return false;
  }
}
