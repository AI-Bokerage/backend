package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.enums.DunningStatus;
import com.company.bsmsvc.domain.port.DunningEventPublisher;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InMemoryDunningEventPublisherAdapter implements DunningEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InMemoryDunningEventPublisherAdapter.class);

    public record PublishedEvent(String kind, UUID subscriptionId, UUID tenantId, String detail) {}

    private final List<PublishedEvent> published = new CopyOnWriteArrayList<>();

    @Override
    public void publishStarted(UUID subscriptionId, UUID tenantId, UUID invoiceId, int attemptNumber) {
        log.info("Dunning started: subscription={} tenant={} invoice={} attempt={}", subscriptionId, tenantId, invoiceId, attemptNumber);
        published.add(new PublishedEvent("STARTED", subscriptionId, tenantId, invoiceId + ":" + attemptNumber));
    }

    @Override
    public void publishRetry(UUID subscriptionId, UUID tenantId, int attemptNumber, DunningStatus newStatus) {
        log.info("Dunning retry: subscription={} tenant={} attempt={} status={}", subscriptionId, tenantId, attemptNumber, newStatus);
        published.add(new PublishedEvent("RETRY", subscriptionId, tenantId, attemptNumber + ":" + newStatus));
    }

    @Override
    public void publishRecovered(UUID subscriptionId, UUID tenantId, UUID invoiceId) {
        log.info("Dunning recovered: subscription={} tenant={} invoice={}", subscriptionId, tenantId, invoiceId);
        published.add(new PublishedEvent("RECOVERED", subscriptionId, tenantId, String.valueOf(invoiceId)));
    }

    @Override
    public void publishSuspended(UUID subscriptionId, UUID tenantId) {
        log.info("Dunning suspended: subscription={} tenant={}", subscriptionId, tenantId);
        published.add(new PublishedEvent("SUSPENDED", subscriptionId, tenantId, null));
    }

    @Override
    public void publishCancelled(UUID subscriptionId, UUID tenantId) {
        log.info("Dunning cancelled: subscription={} tenant={}", subscriptionId, tenantId);
        published.add(new PublishedEvent("CANCELLED", subscriptionId, tenantId, null));
    }

    public List<PublishedEvent> getPublished() {
        return published;
    }
}
