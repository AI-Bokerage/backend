package com.company.audit.demo;

import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.AuditVerifier;
import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.PartitionContext;
import com.company.audit.core.api.VerificationResult;
import com.company.audit.core.api.enums.ActorType;
import com.company.audit.core.api.enums.AuditCategory;
import com.company.audit.spring.anchor.AnchorSummary;
import com.company.audit.spring.ingestion.AuditRecorder;
import com.company.audit.spring.partition.PartitionRegistry;
import com.company.audit.spring.port.EventStore;
import com.company.audit.spring.scheduling.AnchorPublisherJob;
import com.company.audit.spring.verification.ChainVerifierJob;
import com.company.audit.spring.verification.VerificationSummary;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * A manual-poking demo controller exercising {@code audit-core} through the starter.
 */
@RestController
public class DemoController {

    private final AuditRecorder auditRecorder;
    private final AuditVerifier auditVerifier;
    private final PartitionRegistry partitionRegistry;
    private final EventStore eventStore;
    private final ChainVerifierJob chainVerifierJob;
    private final AnchorPublisherJob anchorPublisherJob;
    private final Clock clock;

    /**
     * Creates a new controller.
     *
     * @param auditRecorder the recorder bean provided by the starter
     * @param auditVerifier the verifier bean provided by the starter
     * @param partitionRegistry the partition registry bean provided by the starter
     * @param eventStore the rich event store bean provided by the starter
     * @param chainVerifierJob the scheduled verification job bean provided by the starter
     * @param anchorPublisherJob the scheduled anchor-publishing job bean provided by the starter
     * @param clock the clock bean provided by the starter
     */
    public DemoController(
            AuditRecorder auditRecorder,
            AuditVerifier auditVerifier,
            PartitionRegistry partitionRegistry,
            EventStore eventStore,
            ChainVerifierJob chainVerifierJob,
            AnchorPublisherJob anchorPublisherJob,
            Clock clock) {
        this.auditRecorder = auditRecorder;
        this.auditVerifier = auditVerifier;
        this.partitionRegistry = partitionRegistry;
        this.eventStore = eventStore;
        this.chainVerifierJob = chainVerifierJob;
        this.anchorPublisherJob = anchorPublisherJob;
        this.clock = clock;
    }

    /**
     * Records a new event: appends it to the tamper-evident ledger and persists its full rich
     * copy (payload included), via {@link AuditRecorder}.
     *
     * @param request the event to record
     * @return a summary of the resulting chained record
     */
    @PostMapping("/demo/events")
    public AppendResponse appendEvent(@RequestBody AppendRequest request) {
        AuditEvent event = AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .partitionKey(request.partitionKey())
                .eventType(request.eventType())
                .actorType(ActorType.SERVICE)
                .actorId("demo-actor")
                .category(AuditCategory.DATA_MUTATION)
                .occurredAt(clock.instant())
                .payload(request.payload())
                .build();
        ChainedRecord record = auditRecorder.record(event);
        return new AppendResponse(record.seq(), record.eventHash().hex());
    }

    /**
     * Returns the rich (payload-included) events previously recorded for the given partition —
     * this is what proves the payload survived the round trip through Mongo, since
     * {@link #verify} never could ({@code audit_immutable} never stores it).
     *
     * @param partitionKey the partition to look up
     * @return the partition's recorded events, payload included
     */
    @GetMapping("/demo/events/{partitionKey}")
    public List<AuditEvent> events(@PathVariable String partitionKey) {
        return eventStore.findByPartitionKey(partitionKey);
    }

    /**
     * Verifies the given partition's chain.
     *
     * @param partitionKey the partition to verify
     * @return the verification result
     */
    @GetMapping("/demo/verify/{partitionKey}")
    public VerificationResult verify(@PathVariable String partitionKey) {
        PartitionContext partitionContext = partitionRegistry.resolve(partitionKey);
        return auditVerifier.verify(partitionKey, partitionContext);
    }

    /**
     * Runs the chain-verification job across every known partition immediately, without
     * waiting for its daily cron schedule.
     *
     * @return the run's summary, including per-partition results and timing
     */
    @PostMapping("/demo/admin/verify-all")
    public VerificationSummary verifyAll() {
        return chainVerifierJob.runNow();
    }

    /**
     * Runs the anchor-publishing job across every known partition immediately, without
     * waiting for its daily cron schedule.
     *
     * @return the run's summary, including per-partition storage references and any failures
     */
    @PostMapping("/demo/admin/anchor-all")
    public AnchorSummary anchorAll() {
        return anchorPublisherJob.runNow();
    }

    /**
     * Request body for {@link #appendEvent}.
     *
     * @param partitionKey the partition to append to
     * @param eventType the event's type discriminator
     * @param payload arbitrary freeform detail about the event
     */
    public record AppendRequest(String partitionKey, String eventType, Map<String, Object> payload) {
    }

    /**
     * Response body for {@link #appendEvent}.
     *
     * @param seq the assigned sequence number
     * @param eventHashHex the resulting event hash, in hex
     */
    public record AppendResponse(long seq, String eventHashHex) {
    }
}
