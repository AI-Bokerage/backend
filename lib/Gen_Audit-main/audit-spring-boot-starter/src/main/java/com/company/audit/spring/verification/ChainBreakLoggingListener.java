package com.company.audit.spring.verification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

/**
 * The starter's default reaction to a detected chain break: log it at a severity that would
 * actually page in a real deployment.
 *
 * <p>Its own top-level bean, not an anonymous listener embedded in the auto-configuration class,
 * so it can be unit tested directly, replaced wholesale by a consuming application via
 * {@code @ConditionalOnMissingBean} (rather than only ever getting a second listener alongside
 * this one), and shows up meaningfully by name in a stack trace or thread dump.
 */
public class ChainBreakLoggingListener {

    private static final Logger log = LoggerFactory.getLogger(ChainBreakLoggingListener.class);

    /**
     * Creates a new listener.
     */
    public ChainBreakLoggingListener() {
    }

    /**
     * Logs the detected break at {@code ERROR} — a chain break is always critical and should
     * always page, matching how this event is meant to be treated in a real deployment.
     *
     * @param event the detected chain break
     */
    @EventListener
    public void onChainBreak(AuditChainBreakDetectedEvent event) {
        log.error(
                "Audit chain break detected: partition={} status={} breakAtSeq={} detectedAt={}",
                event.partitionKey(),
                event.result().status(),
                event.result().breakAtSeq().orElse(null),
                event.detectedAt());
    }
}
