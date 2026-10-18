package io.genfin.reconciliation.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.time.ClockProviders;
import io.genfin.reconciliation.event.ComparisonCompleted;
import io.genfin.reconciliation.event.DiscrepancyDetected;
import io.genfin.reconciliation.event.MatchingCompleted;
import io.genfin.reconciliation.event.ReconciliationCompleted;
import io.genfin.reconciliation.event.ReconciliationFailed;
import io.genfin.reconciliation.event.ReconciliationStarted;
import io.genfin.reconciliation.id.ReconciliationBatchId;
import io.genfin.reconciliation.id.ReconciliationId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReconciliationEventsTest {

  @Test
  void collectMatchAnalyzeReconcileEmitsOneEventPerPhase() {
    Reconciliation reconciliation =
        new Reconciliation(ReconciliationId.generate(), ReconciliationBatchId.generate());

    reconciliation.startCollecting(ClockProviders.system());
    reconciliation.startMatching();
    reconciliation.startAnalysis(ClockProviders.system());
    reconciliation.reconcile(ClockProviders.system());

    List<DomainEvent> events = reconciliation.pullEvents();

    assertThat(events).hasSize(4);
    assertThat(events.get(0)).isInstanceOf(ReconciliationStarted.class);
    assertThat(events.get(1)).isInstanceOf(MatchingCompleted.class);
    assertThat(events.get(2)).isInstanceOf(ComparisonCompleted.class);
    assertThat(events.get(3)).isInstanceOf(ReconciliationCompleted.class);
  }

  @Test
  void recordDiscrepancyEmitsADiscrepancyDetectedEvent() {
    Reconciliation reconciliation =
        new Reconciliation(ReconciliationId.generate(), ReconciliationBatchId.generate());

    reconciliation.recordDiscrepancy("amount mismatch", ClockProviders.system());

    assertThat(reconciliation.pullEvents()).singleElement().isInstanceOf(DiscrepancyDetected.class);
  }

  @Test
  void failEmitsAReconciliationFailedEvent() {
    Reconciliation reconciliation =
        new Reconciliation(ReconciliationId.generate(), ReconciliationBatchId.generate());
    reconciliation.startCollecting(ClockProviders.system());
    reconciliation.startMatching();
    reconciliation.startAnalysis(ClockProviders.system());
    reconciliation.pullEvents();

    reconciliation.fail("provider timeout", ClockProviders.system());

    List<DomainEvent> events = reconciliation.pullEvents();
    assertThat(events).singleElement().isInstanceOf(ReconciliationFailed.class);
    assertThat(((ReconciliationFailed) events.get(0)).reason()).isEqualTo("provider timeout");
  }

  @Test
  void pullEventsClearsThePendingQueue() {
    Reconciliation reconciliation =
        new Reconciliation(ReconciliationId.generate(), ReconciliationBatchId.generate());
    reconciliation.startCollecting(ClockProviders.system());

    reconciliation.pullEvents();

    assertThat(reconciliation.pullEvents()).isEmpty();
  }
}
