package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.SubscriptionEventPublisherPort;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InMemorySubscriptionEventPublisherAdapter implements SubscriptionEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(InMemorySubscriptionEventPublisherAdapter.class);

    public record PublishedEvent(String kind, UUID subscriptionId, String detail) {}

    private final List<PublishedEvent> published = new CopyOnWriteArrayList<>();

    @Override
    public void publishCreated(Subscription subscription) {
        log.info("Subscription created: {}", subscription.getId());
        published.add(new PublishedEvent("CREATED", subscription.getId(), null));
    }

    @Override
    public void publishChanged(Subscription subscription, String oldPlanCode, String reason) {
        log.info("Subscription {} changed from {} ({})", subscription.getId(), oldPlanCode, reason);
        published.add(new PublishedEvent("CHANGED", subscription.getId(), oldPlanCode + ":" + reason));
    }

    @Override
    public void publishCanceled(Subscription subscription) {
        log.info("Subscription cancelled: {}", subscription.getId());
        published.add(new PublishedEvent("CANCELED", subscription.getId(), null));
    }

    @Override
    public void publishExpired(Subscription subscription) {
        log.info("Subscription expired: {}", subscription.getId());
        published.add(new PublishedEvent("EXPIRED", subscription.getId(), null));
    }

    @Override
    public void publishRenewed(Subscription subscription) {
        log.info("Subscription renewed: {}", subscription.getId());
        published.add(new PublishedEvent("RENEWED", subscription.getId(), null));
    }

    @Override
    public void publishUpgraded(Subscription subscription, UUID fromPlanVersionId) {
        log.info("Subscription {} upgraded from plan version {}", subscription.getId(), fromPlanVersionId);
        published.add(new PublishedEvent("UPGRADED", subscription.getId(), String.valueOf(fromPlanVersionId)));
    }

    public List<PublishedEvent> getPublished() {
        return published;
    }
}
