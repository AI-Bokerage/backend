package io.genfin.invoice.invoice;

import io.genfin.api.domain.AggregateRoot;
import io.genfin.api.event.EventMetadata;
import io.genfin.api.event.OccurredAt;
import io.genfin.api.id.AggregateId;
import io.genfin.api.id.CorrelationId;
import io.genfin.api.id.EventId;
import io.genfin.api.port.time.ClockProvider;
import io.genfin.api.statemachine.StateMachine;
import io.genfin.api.statemachine.TransitionResult;
import io.genfin.api.validation.Validate;
import io.genfin.invoice.adjustment.Adjustment;
import io.genfin.invoice.attachment.AttachmentReference;
import io.genfin.invoice.discount.Discount;
import io.genfin.invoice.event.InvoiceCancelled;
import io.genfin.invoice.event.InvoiceClosed;
import io.genfin.invoice.event.InvoiceIssued;
import io.genfin.invoice.event.InvoiceOverdue;
import io.genfin.invoice.event.InvoicePaid;
import io.genfin.invoice.event.InvoiceUpdated;
import io.genfin.invoice.event.InvoiceViewed;
import io.genfin.invoice.event.InvoiceVoided;
import io.genfin.invoice.exception.IllegalInvoiceStateTransitionException;
import io.genfin.invoice.id.InvoiceId;
import io.genfin.invoice.id.LineId;
import io.genfin.invoice.lifecycle.InvoiceEvent;
import io.genfin.invoice.lifecycle.InvoiceState;
import io.genfin.invoice.lifecycle.StandardInvoiceEvent;
import io.genfin.invoice.lifecycle.StandardInvoiceState;
import io.genfin.invoice.line.InvoiceLine;
import io.genfin.invoice.metadata.Attributes;
import io.genfin.invoice.metadata.ExtensionProperties;
import io.genfin.invoice.metadata.Metadata;
import io.genfin.invoice.numbering.InvoiceNumber;
import io.genfin.invoice.reference.Reference;
import io.genfin.invoice.reference.ReferenceCollection;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The invoice aggregate root. Contains only financial concepts — no Project/Tenant/Customer/Order/
 * Subscription or any other application concept. Structural edits (lines, header discounts) are
 * only permitted in {@link StandardInvoiceState#DRAFT}; adjustments and payments may apply
 * afterward.
 */
public final class Invoice extends AggregateRoot<InvoiceId> {

  private final Currency currency;
  private final StateMachine<InvoiceState, InvoiceEvent> lifecycle;
  private final List<InvoiceLine> lines = new ArrayList<>();
  private final List<Adjustment> adjustments = new ArrayList<>();
  private final List<Discount> discounts = new ArrayList<>();
  private final List<AttachmentReference> attachments = new ArrayList<>();
  private final List<String> notes = new ArrayList<>();
  private final Set<String> tags = new java.util.LinkedHashSet<>();

  private InvoiceType type;
  private InvoiceNumber number;
  private InvoiceDates dates;
  private Metadata metadata;
  private Attributes attributes;
  private ExtensionProperties extensionProperties;
  private ReferenceCollection references;
  private Money amountPaid;
  private AuditInfo audit;
  private int version;
  private boolean closed;

  Invoice(
      InvoiceId id,
      InvoiceType type,
      Currency currency,
      InvoiceDates dates,
      StateMachine<InvoiceState, InvoiceEvent> lifecycle,
      ClockProvider clockProvider,
      String actor) {
    super(id);
    this.type = Validate.notNull(type, "type must not be null.");
    this.currency = Validate.notNull(currency, "currency must not be null.");
    this.dates = Validate.notNull(dates, "dates must not be null.");
    this.lifecycle = Validate.notNull(lifecycle, "lifecycle must not be null.");
    this.metadata = Metadata.empty();
    this.attributes = Attributes.empty();
    this.extensionProperties = ExtensionProperties.empty();
    this.references = ReferenceCollection.empty();
    this.amountPaid = Money.zero(currency);
    this.version = 0;
    var now = clockProvider.now();
    this.audit = new AuditInfo(now, now, actor, actor);
    registerEvent(new io.genfin.invoice.event.InvoiceCreated(newMetadata(clockProvider), id));
  }

  public InvoiceType type() {
    return type;
  }

  public Currency currency() {
    return currency;
  }

  public Optional<InvoiceNumber> number() {
    return Optional.ofNullable(number);
  }

  public InvoiceState status() {
    return lifecycle.currentState();
  }

  public InvoiceDates dates() {
    return dates;
  }

  public List<InvoiceLine> lines() {
    return List.copyOf(lines);
  }

  public List<Adjustment> adjustments() {
    return List.copyOf(adjustments);
  }

  public List<Discount> discounts() {
    return List.copyOf(discounts);
  }

  public List<AttachmentReference> attachments() {
    return List.copyOf(attachments);
  }

  public List<String> notes() {
    return List.copyOf(notes);
  }

  public Set<String> tags() {
    return Set.copyOf(tags);
  }

  public Metadata metadata() {
    return metadata;
  }

  public Attributes attributes() {
    return attributes;
  }

  public ExtensionProperties extensionProperties() {
    return extensionProperties;
  }

  public ReferenceCollection references() {
    return references;
  }

  public Money amountPaid() {
    return amountPaid;
  }

  public AuditInfo audit() {
    return audit;
  }

  public int version() {
    return version;
  }

  public boolean closed() {
    return closed;
  }

  public void addLine(InvoiceLine line, ClockProvider clockProvider, String actor) {
    requireDraft();
    lines.add(line);
    touch(clockProvider, actor);
  }

  public void removeLine(LineId lineId, ClockProvider clockProvider, String actor) {
    requireDraft();
    lines.removeIf(line -> line.id().equals(lineId));
    touch(clockProvider, actor);
  }

  public void addDiscount(Discount discount, ClockProvider clockProvider, String actor) {
    requireDraft();
    discounts.add(discount);
    touch(clockProvider, actor);
  }

  public void addAdjustment(Adjustment adjustment, ClockProvider clockProvider, String actor) {
    requireNotTerminal();
    adjustments.add(adjustment);
    touch(clockProvider, actor);
  }

  public void addReference(Reference reference, ClockProvider clockProvider, String actor) {
    references = references.add(reference);
    touch(clockProvider, actor);
  }

  public void addAttachment(
      AttachmentReference attachment, ClockProvider clockProvider, String actor) {
    attachments.add(attachment);
    touch(clockProvider, actor);
  }

  public void addNote(String note, ClockProvider clockProvider, String actor) {
    notes.add(note);
    touch(clockProvider, actor);
  }

  public void addTag(String tag, ClockProvider clockProvider, String actor) {
    tags.add(tag);
    touch(clockProvider, actor);
  }

  public void updateMetadata(Metadata metadata, ClockProvider clockProvider, String actor) {
    this.metadata = metadata;
    touch(clockProvider, actor);
  }

  public void updateAttributes(Attributes attributes, ClockProvider clockProvider, String actor) {
    this.attributes = attributes;
    touch(clockProvider, actor);
  }

  public void updateExtensionProperties(
      ExtensionProperties extensionProperties, ClockProvider clockProvider, String actor) {
    this.extensionProperties = extensionProperties;
    touch(clockProvider, actor);
  }

  public void issue(InvoiceNumber number, ClockProvider clockProvider, String actor) {
    Validate.notNull(number, "number must not be null.");
    fire(StandardInvoiceEvent.ISSUE);
    this.number = number;
    this.dates = dates.withIssueDate(clockProvider.now());
    registerEvent(new InvoiceIssued(newMetadata(clockProvider), id(), number));
    touch(clockProvider, actor);
  }

  public void send(ClockProvider clockProvider, String actor) {
    fire(StandardInvoiceEvent.SEND);
    touch(clockProvider, actor);
  }

  public void view(ClockProvider clockProvider, String actor) {
    fire(StandardInvoiceEvent.VIEW);
    registerEvent(new InvoiceViewed(newMetadata(clockProvider), id()));
    touch(clockProvider, actor);
  }

  public void recordPayment(
      Money amount, Money outstandingTotal, ClockProvider clockProvider, String actor) {
    Validate.argument(!amount.isNegative() && !amount.isZero(), "payment amount must be positive.");
    this.amountPaid = amountPaid.add(amount);
    boolean fullyPaid = amountPaid.compareTo(outstandingTotal) >= 0;
    fire(
        fullyPaid
            ? StandardInvoiceEvent.RECORD_FULL_PAYMENT
            : StandardInvoiceEvent.RECORD_PARTIAL_PAYMENT);
    if (fullyPaid) {
      registerEvent(new InvoicePaid(newMetadata(clockProvider), id(), amountPaid));
    } else {
      registerEvent(new InvoiceUpdated(newMetadata(clockProvider), id(), version + 1));
    }
    touch(clockProvider, actor);
  }

  public void markOverdue(ClockProvider clockProvider, String actor) {
    fire(StandardInvoiceEvent.MARK_OVERDUE);
    registerEvent(new InvoiceOverdue(newMetadata(clockProvider), id()));
    touch(clockProvider, actor);
  }

  public void cancel(String reason, ClockProvider clockProvider, String actor) {
    fire(StandardInvoiceEvent.CANCEL);
    registerEvent(new InvoiceCancelled(newMetadata(clockProvider), id(), reason));
    touch(clockProvider, actor);
  }

  public void voidInvoice(String reason, ClockProvider clockProvider, String actor) {
    fire(StandardInvoiceEvent.VOID);
    registerEvent(new InvoiceVoided(newMetadata(clockProvider), id(), reason));
    touch(clockProvider, actor);
  }

  public void refund(ClockProvider clockProvider, String actor) {
    fire(StandardInvoiceEvent.REFUND);
    registerEvent(new InvoiceUpdated(newMetadata(clockProvider), id(), version + 1));
    touch(clockProvider, actor);
  }

  public void close(ClockProvider clockProvider, String actor) {
    requireTerminal();
    this.closed = true;
    registerEvent(new InvoiceClosed(newMetadata(clockProvider), id()));
    touch(clockProvider, actor);
  }

  private void fire(InvoiceEvent event) {
    TransitionResult<InvoiceState> result = lifecycle.fire(event);
    if (!result.isAllowed()) {
      throw new IllegalInvoiceStateTransitionException(
          "Cannot apply "
              + event.code()
              + " while invoice is "
              + lifecycle.currentState().code()
              + ".");
    }
  }

  private void requireDraft() {
    if (!(lifecycle.currentState() instanceof StandardInvoiceState state)
        || state != StandardInvoiceState.DRAFT) {
      throw new IllegalInvoiceStateTransitionException(
          "Invoice content can only be edited while DRAFT.");
    }
  }

  private void requireNotTerminal() {
    if (isTerminal(lifecycle.currentState())) {
      throw new IllegalInvoiceStateTransitionException(
          "Invoice is in a terminal state and cannot be modified.");
    }
  }

  private void requireTerminal() {
    if (!isTerminal(lifecycle.currentState())) {
      throw new IllegalInvoiceStateTransitionException(
          "Invoice must reach a terminal state before it can be closed.");
    }
  }

  private static boolean isTerminal(InvoiceState state) {
    return state == StandardInvoiceState.CANCELLED
        || state == StandardInvoiceState.VOIDED
        || state == StandardInvoiceState.REFUNDED
        || state == StandardInvoiceState.PAID;
  }

  private void touch(ClockProvider clockProvider, String actor) {
    version++;
    audit = audit.touched(clockProvider.now(), actor);
  }

  private EventMetadata newMetadata(ClockProvider clockProvider) {
    return new EventMetadata(
        EventId.generate(),
        OccurredAt.now(clockProvider),
        CorrelationId.generate(),
        AggregateId.of(id().value()));
  }
}
