package io.genfin.providerapi.capability;

/**
 * Whether a provider should capture funds automatically upon authorization succeeding, or leave the
 * authorization held for a later, explicit capture call.
 */
public enum CaptureMode {
  AUTOMATIC,
  MANUAL
}
