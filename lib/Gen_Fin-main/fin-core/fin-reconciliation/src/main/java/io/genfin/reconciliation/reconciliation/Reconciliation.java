package io.genfin.reconciliation.reconciliation;

import io.genfin.api.domain.AggregateRoot;
import io.genfin.api.domain.ValueObject;
import io.genfin.api.event.EventMetadata;
import io.genfin.api.event.OccurredAt;
import io.genfin.api.id.AggregateId;
import io.genfin.api.id.CorrelationId;
import io.genfin.api.id.EventId;
import io.genfin.api.port.time.ClockProvider;
import io.genfin.api.statemachine.StateMachine;
import io.genfin.api.statemachine.TransitionResult;
import io.genfin.api.validation.Validate;
import io.genfin.reconciliation.event.ComparisonCompleted;
import io.genfin.reconciliation.event.DiscrepancyDetected;
import io.genfin.reconciliation.event.MatchingCompleted;
import io.genfin.reconciliation.event.ReconciliationCompleted;
import io.genfin.reconciliation.event.ReconciliationFailed;
import io.genfin.reconciliation.event.ReconciliationStarted;
import io.genfin.reconciliation.id.DiscrepancyId;
import io.genfin.reconciliation.id.MatchId;
import io.genfin.reconciliation.id.ReconciliationBatchId;
import io.genfin.reconciliation.id.ReconciliationId;
import io.genfin.reconciliation.lifecycle.ReconciliationEvent;
import io.genfin.reconciliation.lifecycle.ReconciliationLifecycles;
import io.genfin.reconciliation.lifecycle.ReconciliationStatus;
import io.genfin.reconciliation.lifecycle.StandardReconciliationEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The reconciliation aggregate root. Compares and links records already owned by other engines
 * (payment, refund, settlement, bank record) — it never owns those records directly, only {@link
 * io.genfin.refund.reference.Reference}s to them via its {@link ReconciliationItem}s.
 *
 * <p>Matches and discrepancies are kept as plain identifiers/notes at this stage; the detailed
 * {@code Match}/{@code Discrepancy} models arrive in a later phase.
 *
 * <p>Lifecycle transitions are driven entirely by the SPI-replaceable {@link
 * io.genfin.reconciliation.port.lifecycle.ReconciliationLifecycleProvider}; this aggregate holds no
 * transition table of its own.
 */
public final class Reconciliation extends AggregateRoot<ReconciliationId> {

  private final ReconciliationBatchId batchId;
  private final List<ReconciliationItem> items = new ArrayList<>();
  private final List<MatchId> matches = new ArrayList<>();
  private final List<String> discrepancies = new ArrayList<>();
  private final List<ReconciliationStatus> history = new ArrayList<>();
  private final Map<String, String> metadata = new LinkedHashMap<>();
  private final StateMachine<ReconciliationStatus, ReconciliationEvent> lifecycle;

  public Reconciliation(ReconciliationId id, ReconciliationBatchId batchId) {
    this(id, batchId, ReconciliationLifecycles.created());
  }

  public Reconciliation(
      ReconciliationId id,
      ReconciliationBatchId batchId,
      StateMachine<ReconciliationStatus, ReconciliationEvent> lifecycle) {
    super(id);
    this.batchId = Validate.notNull(batchId, "batchId must not be null.");
    this.lifecycle = Validate.notNull(lifecycle, "lifecycle must not be null.");
    this.history.add(lifecycle.currentState());
  }

  public ReconciliationBatchId batchId() {
    return batchId;
  }

  public ReconciliationStatus status() {
    return lifecycle.currentState();
  }

  public List<ReconciliationItem> items() {
    return List.copyOf(items);
  }

  public List<MatchId> matches() {
    return List.copyOf(matches);
  }

  public List<String> discrepancies() {
    return List.copyOf(discrepancies);
  }

  public List<ReconciliationStatus> history() {
    return List.copyOf(history);
  }

  public Map<String, String> metadata() {
    return Map.copyOf(metadata);
  }

  public void addItem(ReconciliationItem item) {
    items.add(Validate.notNull(item, "item must not be null."));
  }

  public void putMetadata(String key, String value) {
    metadata.put(Validate.notBlank(key, "key must not be blank."), value);
  }

  public void startCollecting(ClockProvider clockProvider) {
    fire(StandardReconciliationEvent.COLLECT);
    registerEvent(new ReconciliationStarted(newMetadata(clockProvider), id()));
  }

  public void startMatching() {
    fire(StandardReconciliationEvent.MATCH);
  }

  public void recordMatch(MatchId matchId) {
    matches.add(Validate.notNull(matchId, "matchId must not be null."));
  }

  public void startAnalysis(ClockProvider clockProvider) {
    fire(StandardReconciliationEvent.ANALYZE);
    registerEvent(new MatchingCompleted(newMetadata(clockProvider), id(), matches.size()));
  }

  public void recordDiscrepancy(String note, ClockProvider clockProvider) {
    discrepancies.add(Validate.notBlank(note, "note must not be blank."));
    registerEvent(
        new DiscrepancyDetected(newMetadata(clockProvider), id(), DiscrepancyId.generate(), note));
  }

  public void reconcile(ClockProvider clockProvider) {
    Validate.state(discrepancies.isEmpty(), "Cannot reconcile while open discrepancies remain.");
    fire(StandardReconciliationEvent.RECONCILE);
    registerEvent(new ComparisonCompleted(newMetadata(clockProvider), id(), items.size()));
    registerEvent(new ReconciliationCompleted(newMetadata(clockProvider), id()));
  }

  public void reconcilePartially(ClockProvider clockProvider) {
    Validate.state(!discrepancies.isEmpty(), "Cannot partially reconcile without a discrepancy.");
    fire(StandardReconciliationEvent.PARTIALLY_RECONCILE);
    registerEvent(new ComparisonCompleted(newMetadata(clockProvider), id(), items.size()));
    registerEvent(new ReconciliationCompleted(newMetadata(clockProvider), id()));
  }

  public void fail(String reason, ClockProvider clockProvider) {
    fire(StandardReconciliationEvent.FAIL);
    registerEvent(new ReconciliationFailed(newMetadata(clockProvider), id(), reason));
  }

  public void cancel() {
    fire(StandardReconciliationEvent.CANCEL);
  }

  public void retry() {
    fire(StandardReconciliationEvent.COLLECT);
  }

  public Summary summary() {
    return new Summary(items.size(), matches.size(), discrepancies.size(), status());
  }

  private void fire(ReconciliationEvent event) {
    TransitionResult<ReconciliationStatus> result = lifecycle.fire(event);
    if (!result.isAllowed()) {
      throw new IllegalStateException(
          "Cannot apply "
              + event.code()
              + " while reconciliation is "
              + lifecycle.currentState().code()
              + ".");
    }
    history.add(result.currentState());
  }

  private EventMetadata newMetadata(ClockProvider clockProvider) {
    return new EventMetadata(
        EventId.generate(),
        OccurredAt.now(clockProvider),
        CorrelationId.generate(),
        AggregateId.of(id().value()));
  }

  /** A point-in-time count summary of this reconciliation. */
  public record Summary(
      int itemCount, int matchCount, int discrepancyCount, ReconciliationStatus status)
      implements ValueObject {}
}
