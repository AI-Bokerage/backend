package com.company.bsmsvc.messaging;

import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.domain.model.SubscriptionAddOn;
import com.company.bsmsvc.domain.port.AddOnEventPublisherPort;
import com.company.bsmsvc.infrastructure.client.ppm.PpmAddOnCatalogClient;
import com.company.bsmsvc.infrastructure.client.ppm.PpmAddOnCatalogResult;
import com.company.bsmsvc.infrastructure.outbox.BsmOutboxService;
import com.company.bsmsvc.messaging.BsmMessagingRouting;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Persists add-on lifecycle events to the BSM outbox table within the
 * caller's current transaction. The outbox poller dispatches them to RabbitMQ
 * after the transaction commits, guaranteeing at-least-once delivery.
 *
 * <p>Enriches each event with the add-on's {@code code}/{@code type} from
 * PPM-SVC's catalog. This lookup is best-effort: a failure here must never
 * roll back a purchase/removal that has already been persisted and charged,
 * so failures are logged and the event is still published with a null
 * code/type rather than propagating the exception.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BsmAddOnEventPublisher implements AddOnEventPublisherPort {

    private static final String AGGREGATE_TYPE = "SubscriptionAddOn";

    private final BsmOutboxService       outboxService;
    private final PpmAddOnCatalogClient  ppmAddOnCatalogClient;

    public void publishActivated(SubscriptionAddOn addOn) {
        persist(addOn, "AddOnActivated", BsmMessagingRouting.BSM_ADDON_ACTIVATED);
    }

    public void publishDeactivated(SubscriptionAddOn addOn) {
        persist(addOn, "AddOnDeactivated", BsmMessagingRouting.BSM_ADDON_DEACTIVATED);
    }

    private void persist(SubscriptionAddOn addOn, String eventType, String routingKey) {
        PpmAddOnCatalogResult catalog = resolveCatalog(addOn.getPpmAddOnId());

        AddOnEventPayload payload = new AddOnEventPayload(
            AddOnEventPayload.CURRENT_VERSION,
            eventType,
            addOn.getTenantId(),
            addOn.getSubscriptionId(),
            addOn.getId(),
            addOn.getPpmAddOnId(),
            catalog != null ? catalog.code() : null,
            catalog != null ? catalog.type() : null,
            catalog != null ? catalog.quotaMeterCode() : null,
            catalog != null ? catalog.quotaAmount() : null,
            Instant.now()
        );
        outboxService.save(AGGREGATE_TYPE, addOn.getId(), eventType, routingKey, payload);
        log.debug("[BSM-ADDON-EVENT] Queued outbox event={} subscriptionAddOnId={} tenantId={} addOnCode={}",
            eventType, addOn.getId(), addOn.getTenantId(), payload.addOnCode());
    }

    /**
     * Best-effort enrichment — never throws. Returns null on any PPM
     * integration failure, so callers proceed with a code/type-less event
     * rather than block the (already-charged) purchase or removal.
     */
    private PpmAddOnCatalogResult resolveCatalog(java.util.UUID ppmAddOnId) {
        try {
            return ppmAddOnCatalogClient.getById(ppmAddOnId);
        } catch (PpmIntegrationException e) {
            log.warn("[BSM-ADDON-EVENT] PPM catalog lookup failed for ppmAddOnId={}: {} — publishing without code/type",
                ppmAddOnId, e.getMessage());
            return null;
        }
    }
}
