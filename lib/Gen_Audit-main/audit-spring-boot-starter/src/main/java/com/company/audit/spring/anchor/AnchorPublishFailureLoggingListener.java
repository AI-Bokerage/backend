package com.company.audit.spring.anchor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

/**
 * The starter's default reaction to an anchor-publish failure: log it at a severity that would
 * actually page in a real deployment, matching the source spec's
 * {@code AUDIT_ANCHOR_PUBLISH_FAILED} classification as {@code critical}.
 *
 * <p>Its own top-level bean, not an anonymous listener embedded in the auto-configuration class
 * — same reasoning as {@code ChainBreakLoggingListener} from the scheduled-verification phase:
 * unit-testable directly, replaceable wholesale via {@code @ConditionalOnMissingBean} rather than
 * only ever getting a second listener alongside it, and shows up meaningfully by name in a stack
 * trace or thread dump.
 */
public class AnchorPublishFailureLoggingListener {

    private static final Logger log = LoggerFactory.getLogger(AnchorPublishFailureLoggingListener.class);

    /**
     * Creates a new listener.
     */
    public AnchorPublishFailureLoggingListener() {
    }

    /**
     * Logs the failure at {@code ERROR}.
     *
     * @param event the anchor-publish failure
     */
    @EventListener
    public void onAnchorPublishFailed(AnchorPublishFailedEvent event) {
        log.error(
                "Audit anchor publish failed: partition={} detectedAt={}",
                event.partitionKey(),
                event.detectedAt(),
                event.cause());
    }
}
