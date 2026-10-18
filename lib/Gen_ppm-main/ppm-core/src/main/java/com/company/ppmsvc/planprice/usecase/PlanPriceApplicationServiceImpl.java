package com.company.ppmsvc.planprice.usecase;

import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.planprice.model.PlanPrice;
import com.company.ppmsvc.planprice.port.PlanPriceRepositoryPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanPriceApplicationServiceImpl implements PlanPriceApplicationService {

    private final PlanPriceRepositoryPort planPriceRepository;
    private final PlanRepositoryPort      planRepository;

    // ── createPrice ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public PlanPrice createPrice(UUID actorId, UUID planId, BillingCycle cycle, String currency,
            String region, BigDecimal amount, Boolean taxInclusive, LocalDate effectiveFrom) {

        // BR-1: referenced plan must exist
        planRepository.findById(planId)
            .orElseThrow(() -> {
                log.warn("plan.not_found id={}", planId);
                return new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + planId);
            });

        // BR-2: amount must be positive
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Price amount must be greater than zero.");
        }

        // BR-3 + BR-4: normalise currency and region to uppercase
        String normalizedCurrency = currency.strip().toUpperCase();
        String normalizedRegion   = region.strip().toUpperCase();

        // BR-7: duplicate active pricing key check before write
        if (planPriceRepository.exists(planId, normalizedRegion, normalizedCurrency, cycle, effectiveFrom)) {
            log.warn("plan_price.duplicate planId={} region={} currency={} cycle={}",
                planId, normalizedRegion, normalizedCurrency, cycle.getValue());
            throw new BusinessException(ErrorCode.PLAN_PRICE_ALREADY_EXISTS,
                "A price already exists for plan=" + planId
                    + " region=" + normalizedRegion + " currency=" + normalizedCurrency
                    + " cycle=" + cycle.getValue()
                    + " effectiveFrom=" + effectiveFrom);
        }

        // BR-5: taxInclusive defaults to false
        boolean resolvedTaxInclusive = taxInclusive != null ? taxInclusive : false;

        Instant now = Instant.now();
        PlanPrice price = PlanPrice.builder()
            .id(UUID.randomUUID())
            // version null → new entity → Spring Data calls persist() not merge()
            .planId(planId)
            .cycle(cycle)
            .currency(normalizedCurrency)
            .region(normalizedRegion)
            .amount(amount)
            .taxInclusive(resolvedTaxInclusive)
            .effectiveFrom(effectiveFrom)
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(actorId)
            .updatedBy(actorId)
            .build();

        PlanPrice saved = planPriceRepository.save(price);
        log.info("Plan price created id={} planId={} region={} currency={} cycle={} effectiveFrom={}",
            saved.getId(), saved.getPlanId(), saved.getRegion(), saved.getCurrency(),
            saved.getCycle().getValue(), saved.getEffectiveFrom());
        return saved;
    }

    // ── updatePrice ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public PlanPrice updatePrice(UUID actorId, UUID priceId, BigDecimal amount, Boolean taxInclusive, Boolean active) {

        PlanPrice existing = planPriceRepository.findById(priceId)
            .orElseThrow(() -> {
                log.warn("plan_price.not_found id={}", priceId);
                return new ResourceNotFoundException(
                    ErrorCode.PLAN_PRICE_NOT_FOUND, "Plan price not found: " + priceId);
            });

        // BR-2: if a new amount is supplied it must be positive
        if (amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Price amount must be greater than zero.");
        }

        PlanPrice updated = PlanPrice.builder()
            .id(existing.getId())
            .version(existing.getVersion())
            // Immutable identity fields — always carried forward unchanged
            .planId(existing.getPlanId())
            .cycle(existing.getCycle())
            .currency(existing.getCurrency())
            .region(existing.getRegion())
            .effectiveFrom(existing.getEffectiveFrom())
            // Mutable fields
            .amount(amount != null ? amount : existing.getAmount())
            .taxInclusive(taxInclusive != null ? taxInclusive : existing.isTaxInclusive())
            .active(active != null ? active : existing.isActive())
            // Audit — createdAt/createdBy preserved; updatedAt/updatedBy refreshed
            .createdAt(existing.getCreatedAt())
            .createdBy(existing.getCreatedBy())
            .updatedAt(Instant.now())
            .updatedBy(actorId)
            .build();

        PlanPrice saved = planPriceRepository.save(updated);
        log.info("Plan price updated id={}", saved.getId());
        return saved;
    }

    // ── getPrice ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PlanPrice getPrice(UUID priceId) {
        log.debug("plan_price.get id={}", priceId);
        return planPriceRepository.findById(priceId)
            .orElseThrow(() -> {
                log.warn("plan_price.not_found id={}", priceId);
                return new ResourceNotFoundException(
                    ErrorCode.PLAN_PRICE_NOT_FOUND, "Plan price not found: " + priceId);
            });
    }

    // ── listPrices ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<PlanPrice> listPrices(UUID planId, String region, String currency, BillingCycle cycle, Boolean active) {
        log.debug("plan_price.list planId={} region={} currency={} cycle={}", planId, region, currency, cycle);
        List<PlanPrice> all = planId != null
            ? planPriceRepository.findByPlanId(planId)
            : planPriceRepository.findAll();

        return all.stream()
            .filter(p -> region   == null || p.getRegion().equalsIgnoreCase(region))
            .filter(p -> currency == null || p.getCurrency().equalsIgnoreCase(currency))
            .filter(p -> cycle    == null || p.getCycle() == cycle)
            .filter(p -> active   == null || p.isActive() == active)
            .toList();
    }

    // ── deletePrice ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deletePrice(UUID actorId, UUID priceId) {
        planPriceRepository.softDelete(priceId, actorId);
        log.info("Plan price soft-deleted id={}", priceId);
    }
}
