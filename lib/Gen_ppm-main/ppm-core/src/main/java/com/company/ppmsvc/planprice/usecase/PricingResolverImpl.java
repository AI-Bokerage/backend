package com.company.ppmsvc.planprice.usecase;

import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.planprice.model.PlanPrice;
import com.company.ppmsvc.planprice.port.PlanPriceRepositoryPort;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pure read implementation of the Pricing Resolver Engine (PPM-09).
 *
 * <p>No write operations. No promo application. No currency conversion.
 * No regional fallback. Exact region and currency match only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingResolverImpl implements PricingResolver {

    private final PlanRepositoryPort      planRepository;
    private final PlanPriceRepositoryPort planPriceRepository;

    @Override
    @Transactional(readOnly = true)
    public PlanPrice resolvePrice(UUID planId, String region, String currency, BillingCycle cycle) {
        // PR-1: plan must exist
        planRepository.findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + planId));

        // PR-2: normalise region and currency (exact match, no fallback)
        String normalizedRegion   = region.strip().toUpperCase();
        String normalizedCurrency = currency.strip().toUpperCase();

        // PR-3: load candidate rows for (planId, region, currency)
        List<PlanPrice> candidates =
            planPriceRepository.findByPlanIdAndRegionAndCurrency(planId, normalizedRegion, normalizedCurrency);

        // PR-4 active, PR-5 effectiveFrom <= today, PR-6 latest effectiveFrom wins
        LocalDate today = LocalDate.now();
        PlanPrice resolved = candidates.stream()
            .filter(PlanPrice::isActive)
            .filter(p -> p.getCycle() == cycle)
            .filter(p -> !p.getEffectiveFrom().isAfter(today))
            .max(Comparator.comparing(PlanPrice::getEffectiveFrom))
            .orElseThrow(() -> {
                log.debug("No applicable price: planId={} region={} currency={} cycle={}",
                    planId, normalizedRegion, normalizedCurrency, cycle.getValue());
                return new ResourceNotFoundException(
                    ErrorCode.PLAN_PRICE_NOT_RESOLVED,
                    "No active price for plan=" + planId
                        + " region=" + normalizedRegion
                        + " currency=" + normalizedCurrency
                        + " cycle=" + cycle.getValue());
            });

        log.debug("Resolved price: priceId={} effectiveFrom={} amount={}",
            resolved.getId(), resolved.getEffectiveFrom(), resolved.getAmount());
        return resolved;
    }
}
