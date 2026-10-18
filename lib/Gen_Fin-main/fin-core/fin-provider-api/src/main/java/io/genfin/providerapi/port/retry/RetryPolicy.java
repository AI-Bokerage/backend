package io.genfin.providerapi.port.retry;

import io.genfin.api.port.spi.Extension;
import io.genfin.providerapi.retry.RetryClassification;
import io.genfin.providerapi.retry.RetryDecision;

public interface RetryPolicy extends Extension {

  RetryDecision decide(int attemptNumber, RetryClassification classification);
}
