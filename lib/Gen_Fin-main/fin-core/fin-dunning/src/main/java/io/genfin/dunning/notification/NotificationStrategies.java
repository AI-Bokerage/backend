package io.genfin.dunning.notification;

import io.genfin.dunning.internal.notification.DefaultNotificationStrategy;
import io.genfin.dunning.port.notification.NotificationStrategy;

/**
 * Factory for {@link NotificationStrategy} instances. For anything other than the standard
 * preference-over-fallback resolution, an application implements {@link NotificationStrategy}
 * directly.
 */
public final class NotificationStrategies {

  private static final NotificationStrategy STANDARD = new DefaultNotificationStrategy();

  private NotificationStrategies() {}

  /** Applies a {@link NotificationSchedule}'s preferences over each occurrence's fallback. */
  public static NotificationStrategy standard() {
    return STANDARD;
  }
}
