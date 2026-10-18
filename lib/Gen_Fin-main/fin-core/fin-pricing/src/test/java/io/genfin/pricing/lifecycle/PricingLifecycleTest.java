package io.genfin.pricing.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.statemachine.StateMachine;
import org.junit.jupiter.api.Test;

/** Exercises the standard {@link PricingLifecycleProvider} state machine directly. */
class PricingLifecycleTest {

  @Test
  void standardLifecycleFollowsCreatedValidatingCalculatingPricedPath() {
    StateMachine<PricingStatus, PricingEvent> lifecycle = PricingLifecycles.created();

    assertThat(lifecycle.fire(PricingRequestEvent.VALIDATE).isAllowed()).isTrue();
    assertThat(lifecycle.fire(PricingRequestEvent.CALCULATE).isAllowed()).isTrue();
    assertThat(lifecycle.fire(PricingRequestEvent.COMPLETE).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(PricingRequestStatus.PRICED);
  }

  @Test
  void createdCannotJumpDirectlyToCalculating() {
    StateMachine<PricingStatus, PricingEvent> lifecycle = PricingLifecycles.created();

    assertThat(lifecycle.fire(PricingRequestEvent.CALCULATE).isAllowed()).isFalse();
    assertThat(lifecycle.currentState()).isEqualTo(PricingRequestStatus.CREATED);
  }

  @Test
  void createdCanExpireDirectlyWithoutValidating() {
    StateMachine<PricingStatus, PricingEvent> lifecycle = PricingLifecycles.created();

    assertThat(lifecycle.fire(PricingRequestEvent.EXPIRE).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(PricingRequestStatus.EXPIRED);
  }

  @Test
  void validatingCanBeRejectedOrExpired() {
    StateMachine<PricingStatus, PricingEvent> rejected = PricingLifecycles.created();
    rejected.fire(PricingRequestEvent.VALIDATE);
    assertThat(rejected.fire(PricingRequestEvent.REJECT).isAllowed()).isTrue();
    assertThat(rejected.currentState()).isEqualTo(PricingRequestStatus.REJECTED);

    StateMachine<PricingStatus, PricingEvent> expired = PricingLifecycles.created();
    expired.fire(PricingRequestEvent.VALIDATE);
    assertThat(expired.fire(PricingRequestEvent.EXPIRE).isAllowed()).isTrue();
    assertThat(expired.currentState()).isEqualTo(PricingRequestStatus.EXPIRED);
  }

  @Test
  void calculatingCanBeRejectedOrExpired() {
    StateMachine<PricingStatus, PricingEvent> rejected = PricingLifecycles.created();
    rejected.fire(PricingRequestEvent.VALIDATE);
    rejected.fire(PricingRequestEvent.CALCULATE);
    assertThat(rejected.fire(PricingRequestEvent.REJECT).isAllowed()).isTrue();
    assertThat(rejected.currentState()).isEqualTo(PricingRequestStatus.REJECTED);
  }

  @Test
  void pricedRejectedAndExpiredAreTerminal() {
    StateMachine<PricingStatus, PricingEvent> lifecycle = PricingLifecycles.created();
    lifecycle.fire(PricingRequestEvent.VALIDATE);
    lifecycle.fire(PricingRequestEvent.CALCULATE);
    lifecycle.fire(PricingRequestEvent.COMPLETE);

    assertThat(lifecycle.fire(PricingRequestEvent.REJECT).isAllowed()).isFalse();
    assertThat(lifecycle.currentState()).isEqualTo(PricingRequestStatus.PRICED);
  }
}
