package com.company.audit.spring.verification;

import com.company.audit.core.api.VerificationResult;
import java.time.Instant;

/**
 * Published when {@link ChainVerifierJob} finds a partition whose chain did not verify as
 * {@code OK}.
 *
 * <p>A plain immutable object, not a subclass of Spring's {@code ApplicationEvent} — a plain
 * object consumed via {@code @EventListener} is the current, less ceremonious idiom, and it's
 * exactly the mechanism this project uses rather than inventing a bespoke listener port: Spring
 * already solved "notify interested parties without the publisher hardcoding what happens
 * downstream."
 *
 * <p>{@code detectedAt} is not redundant with anything on {@link VerificationResult}, which
 * carries no timestamp of its own.
 *
 * @param partitionKey the partition whose chain did not verify as {@code OK}
 * @param result the verification result that triggered this event
 * @param detectedAt the instant the break was detected
 */
public record AuditChainBreakDetectedEvent(String partitionKey, VerificationResult result, Instant detectedAt) {
}
