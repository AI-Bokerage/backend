package io.genfin.reconciliation.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.spi.ExtensionRegistries;
import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.api.time.ClockProviders;
import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.reconciliation.id.MatchId;
import io.genfin.reconciliation.id.ReconciliationBatchId;
import io.genfin.reconciliation.id.ReconciliationId;
import io.genfin.reconciliation.id.ReconciliationItemId;
import io.genfin.reconciliation.lifecycle.StandardReconciliationStatus;
import io.genfin.refund.reference.Reference;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ReconciliationTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency()
          .code("USD")
          .symbol("$")
          .displayName("US Dollar")
          .fractionDigits(2)
          .build();

  @Test
  void newReconciliationStartsInCreatedStateWithNoItems() {
    Reconciliation reconciliation =
        new Reconciliation(ReconciliationId.generate(), ReconciliationBatchId.generate());

    assertThat(reconciliation.status()).isEqualTo(StandardReconciliationStatus.CREATED);
    assertThat(reconciliation.items()).isEmpty();
    assertThat(reconciliation.history()).containsExactly(StandardReconciliationStatus.CREATED);
  }

  @Test
  void addItemAndPutMetadataAccumulateOnTheAggregate() {
    Reconciliation reconciliation =
        new Reconciliation(ReconciliationId.generate(), ReconciliationBatchId.generate());
    ReconciliationItem item =
        ReconciliationItem.of(Reference.payment("PAY-1"), Money.of(new BigDecimal("10.00"), USD));

    reconciliation.addItem(item);
    reconciliation.putMetadata("batch-source", "provider-export");

    assertThat(reconciliation.items()).containsExactly(item);
    assertThat(reconciliation.metadata()).containsEntry("batch-source", "provider-export");
  }

  @Test
  void recordMatchAndSummaryReflectCurrentCounts() {
    Reconciliation reconciliation =
        new Reconciliation(ReconciliationId.generate(), ReconciliationBatchId.generate());
    reconciliation.addItem(
        ReconciliationItem.of(Reference.payment("PAY-1"), Money.of(new BigDecimal("10.00"), USD)));
    reconciliation.recordMatch(MatchId.generate());

    Reconciliation.Summary summary = reconciliation.summary();

    assertThat(summary.itemCount()).isEqualTo(1);
    assertThat(summary.matchCount()).isEqualTo(1);
    assertThat(summary.discrepancyCount()).isZero();
    assertThat(summary.status()).isEqualTo(StandardReconciliationStatus.CREATED);
  }

  @Test
  void reconcileWithOpenDiscrepanciesIsRejected() {
    Reconciliation reconciliation =
        new Reconciliation(ReconciliationId.generate(), ReconciliationBatchId.generate());
    reconciliation.startCollecting(ClockProviders.system());
    reconciliation.startMatching();
    reconciliation.startAnalysis(ClockProviders.system());
    reconciliation.recordDiscrepancy("amount mismatch", ClockProviders.system());

    assertThatThrownBy(() -> reconciliation.reconcile(ClockProviders.system()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void reconcilePartiallyRequiresAnOpenDiscrepancy() {
    Reconciliation reconciliation =
        new Reconciliation(ReconciliationId.generate(), ReconciliationBatchId.generate());
    reconciliation.startCollecting(ClockProviders.system());
    reconciliation.startMatching();
    reconciliation.startAnalysis(ClockProviders.system());

    assertThatThrownBy(() -> reconciliation.reconcilePartially(ClockProviders.system()))
        .isInstanceOf(IllegalStateException.class);

    reconciliation.recordDiscrepancy("amount mismatch", ClockProviders.system());

    reconciliation.reconcilePartially(ClockProviders.system());

    assertThat(reconciliation.status())
        .isEqualTo(StandardReconciliationStatus.PARTIALLY_RECONCILED);
  }

  @Test
  void cancelMovesCreatedReconciliationsToCancelled() {
    Reconciliation reconciliation =
        new Reconciliation(ReconciliationId.generate(), ReconciliationBatchId.generate());

    reconciliation.cancel();

    assertThat(reconciliation.status()).isEqualTo(StandardReconciliationStatus.CANCELLED);
  }

  @Test
  void retryMovesAFailedReconciliationBackToCollecting() {
    Reconciliation reconciliation =
        new Reconciliation(ReconciliationId.generate(), ReconciliationBatchId.generate());
    reconciliation.startCollecting(ClockProviders.system());
    reconciliation.startMatching();
    reconciliation.startAnalysis(ClockProviders.system());
    reconciliation.fail("provider timeout", ClockProviders.system());

    reconciliation.retry();

    assertThat(reconciliation.status()).isEqualTo(StandardReconciliationStatus.COLLECTING);
  }

  @Test
  void firingAnIllegalEventThrowsWithTheOffendingStateAndEventInTheMessage() {
    Reconciliation reconciliation =
        new Reconciliation(ReconciliationId.generate(), ReconciliationBatchId.generate());

    assertThatThrownBy(reconciliation::startMatching).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void builderAssemblesItemsAndMetadataInOneExpression() {
    ReconciliationItem item =
        ReconciliationItemBuilder.newItem()
            .source(Reference.payment("PAY-1"))
            .amount(Money.of(new BigDecimal("10.00"), USD))
            .build();

    Reconciliation reconciliation =
        ReconciliationBuilder.newReconciliation()
            .batchId(ReconciliationBatchId.generate())
            .item(item)
            .metadata("channel", "web")
            .build();

    assertThat(reconciliation.items()).containsExactly(item);
    assertThat(reconciliation.metadata()).containsEntry("channel", "web");
    assertThat(reconciliation.status()).isEqualTo(StandardReconciliationStatus.CREATED);
  }

  @Test
  void itemBuilderGeneratesAnIdWhenNoneIsSupplied() {
    ReconciliationItem item =
        ReconciliationItemBuilder.newItem()
            .source(Reference.payment("PAY-1"))
            .amount(Money.of(new BigDecimal("10.00"), USD))
            .build();

    assertThat(item.id()).isNotNull();
  }

  @Test
  void itemBuilderHonoursAnExplicitId() {
    ReconciliationItemId id = ReconciliationItemId.generate();

    ReconciliationItem item =
        ReconciliationItemBuilder.newItem()
            .id(id)
            .source(Reference.payment("PAY-1"))
            .amount(Money.of(new BigDecimal("10.00"), USD))
            .build();

    assertThat(item.id()).isEqualTo(id);
  }

  @Test
  void factoryFallsBackToTheStandardLifecycleWhenNoneIsRegistered() {
    ExtensionRegistry registry = ExtensionRegistries.create();
    Reconciliation reconciliation =
        ReconciliationFactory.create(registry, ReconciliationBatchId.generate());

    assertThat(reconciliation.status()).isEqualTo(StandardReconciliationStatus.CREATED);
  }
}
