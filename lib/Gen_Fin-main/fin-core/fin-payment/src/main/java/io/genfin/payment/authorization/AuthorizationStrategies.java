package io.genfin.payment.authorization;

import io.genfin.payment.internal.authorization.AutomaticCaptureStrategy;
import io.genfin.payment.internal.authorization.FullAuthorizationStrategy;
import io.genfin.payment.internal.authorization.ManualCaptureStrategy;
import io.genfin.payment.internal.authorization.PartialAuthorizationStrategy;
import io.genfin.payment.port.authorization.AuthorizationStrategy;
import io.genfin.payment.port.authorization.CaptureStrategy;

public final class AuthorizationStrategies {

  private static final CaptureStrategy AUTOMATIC_CAPTURE = new AutomaticCaptureStrategy();
  private static final CaptureStrategy MANUAL_CAPTURE = new ManualCaptureStrategy();
  private static final AuthorizationStrategy FULL_AUTHORIZATION = new FullAuthorizationStrategy();
  private static final AuthorizationStrategy PARTIAL_AUTHORIZATION =
      new PartialAuthorizationStrategy();

  private AuthorizationStrategies() {}

  public static CaptureStrategy automaticCapture() {
    return AUTOMATIC_CAPTURE;
  }

  public static CaptureStrategy manualCapture() {
    return MANUAL_CAPTURE;
  }

  public static AuthorizationStrategy fullAuthorization() {
    return FULL_AUTHORIZATION;
  }

  public static AuthorizationStrategy partialAuthorization() {
    return PARTIAL_AUTHORIZATION;
  }
}
