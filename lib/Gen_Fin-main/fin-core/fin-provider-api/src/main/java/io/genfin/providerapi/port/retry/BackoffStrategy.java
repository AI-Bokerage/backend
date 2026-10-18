package io.genfin.providerapi.port.retry;

import io.genfin.api.port.spi.Extension;
import java.time.Duration;

public interface BackoffStrategy extends Extension {

  Duration nextDelay(int attemptNumber);
}
