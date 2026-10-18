package com.company.audit.spring.anchor;

import java.time.Instant;

/**
 * Published when {@code AnchorPublisherJob} fails to publish an anchor for a partition.
 *
 * <p>A plain immutable object consumed via {@code @EventListener}, same idiom as
 * {@code AuditChainBreakDetectedEvent} from the scheduled-verification phase — Spring's own
 * eventing already solves "notify interested parties without the publisher hardcoding what
 * happens downstream," so no bespoke listener port was invented for this either.
 *
 * @param partitionKey the partition whose anchor publish failed
 * @param cause the exception that caused the failure
 * @param detectedAt the instant the failure was detected
 */
public record AnchorPublishFailedEvent(String partitionKey, Exception cause, Instant detectedAt) {
}
