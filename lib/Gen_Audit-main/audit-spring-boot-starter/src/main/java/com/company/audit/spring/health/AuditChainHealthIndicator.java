package com.company.audit.spring.health;

import com.company.audit.spring.anchor.AnchorSummary;
import com.company.audit.spring.scheduling.AnchorPublisherJob;
import com.company.audit.spring.verification.ChainVerifierJob;
import com.company.audit.spring.verification.VerificationSummary;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Reports the audit ledger's health from the most recently completed scheduled (or manually
 * triggered) verification and anchoring runs.
 *
 * <p><b>Hard rule: this class never performs verification or anchoring itself.</b> It only ever
 * reads {@link ChainVerifierJob#lastSummary()} and {@link AnchorPublisherJob#lastSummary()} — the
 * cached state each job updates at the end of its own {@code runNow()}. A health endpoint has to
 * stay cheap and fast; triggering a real chain walk (or an S3 call) from something a load
 * balancer might poll every few seconds would defeat that entirely. This class contains no
 * dependency capable of calling {@code runNow()} or touching Postgres/S3 directly — it holds
 * only the two jobs' cached summaries, never their other collaborators.
 *
 * <p>Status ({@code UP}/{@code DOWN}) is driven only by the last verification run's clean/broken
 * counts, not by anchoring outcomes — a partition failing to anchor is a real problem worth
 * surfacing (see its timestamp in the health details), but it is not evidence that the ledger
 * itself is broken, which is what this indicator's status is meant to answer.
 *
 * <p>Anchoring is optional infrastructure (gated behind AWS availability), so
 * {@link AnchorPublisherJob} is looked up via {@link ObjectProvider} rather than required —
 * unlike {@code MeterRegistry} elsewhere in this starter, {@link AnchorPublisherJob} is this
 * starter's own class, always present on the classpath regardless of whether its bean is
 * registered, so an {@link ObjectProvider} lookup carries none of the eager-classloading risk
 * documented on {@code AuditMetricsRecorder} — this is purely "is the bean registered," not "is
 * the type loadable."
 */
public class AuditChainHealthIndicator implements HealthIndicator {

    private final ChainVerifierJob chainVerifierJob;
    private final ObjectProvider<AnchorPublisherJob> anchorPublisherJobProvider;

    /**
     * Creates a new health indicator.
     *
     * @param chainVerifierJob the scheduled verification job whose cached last summary is read
     * @param anchorPublisherJobProvider provides the scheduled anchoring job, if one is
     *     registered
     */
    public AuditChainHealthIndicator(
            ChainVerifierJob chainVerifierJob, ObjectProvider<AnchorPublisherJob> anchorPublisherJobProvider) {
        this.chainVerifierJob = chainVerifierJob;
        this.anchorPublisherJobProvider = anchorPublisherJobProvider;
    }

    @Override
    public Health health() {
        VerificationSummary lastVerification = chainVerifierJob.lastSummary();
        if (lastVerification == null) {
            return Health.unknown().withDetail("reason", "no verification run has completed yet").build();
        }

        Health.Builder builder = lastVerification.brokenCount() == 0 ? Health.up() : Health.down();
        builder.withDetail("cleanPartitions", lastVerification.cleanCount())
                .withDetail("brokenPartitions", lastVerification.brokenCount())
                .withDetail("lastVerificationRunAt", lastVerification.finishedAt());

        AnchorPublisherJob anchorPublisherJob = anchorPublisherJobProvider.getIfAvailable();
        AnchorSummary lastAnchor = anchorPublisherJob != null ? anchorPublisherJob.lastSummary() : null;
        if (lastAnchor != null) {
            builder.withDetail("lastAnchorRunAt", lastAnchor.finishedAt());
        }

        return builder.build();
    }
}
