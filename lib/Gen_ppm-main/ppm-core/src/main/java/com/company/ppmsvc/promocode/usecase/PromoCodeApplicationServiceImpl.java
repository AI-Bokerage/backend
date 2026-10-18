package com.company.ppmsvc.promocode.usecase;

import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.promocode.model.DiscountType;
import com.company.ppmsvc.promocode.model.PromoCode;
import com.company.ppmsvc.promocode.port.PromoCodeRepositoryPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromoCodeApplicationServiceImpl implements PromoCodeApplicationService {

    private final PromoCodeRepositoryPort promoCodeRepository;

    // ── createPromoCode ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public PromoCode createPromoCode(UUID actorId, String code, DiscountType type, BigDecimal value,
                                      LocalDate validFrom, LocalDate validUntil, Integer usageCap,
                                      Boolean firstTimeOnly, Boolean active) {
        // BR-2: normalise code before uniqueness check and persistence
        String normalizedCode = code.strip().toUpperCase();

        // BR-1: code must be unique among active rows
        if (promoCodeRepository.existsByCode(normalizedCode)) {
            log.warn("promo_code.conflict code={}", normalizedCode);
            throw new BusinessException(ErrorCode.PROMO_CODE_ALREADY_EXISTS,
                "A promo code with code '" + normalizedCode + "' already exists.");
        }

        // BR-3: value validation
        validateValue(type, value);

        // BR-4: date range validation
        validateDateRange(validFrom, validUntil);

        // BR-5: defaults
        boolean firstTimeOnlyValue = firstTimeOnly != null ? firstTimeOnly : false;
        boolean activeValue        = active        != null ? active        : true;

        Instant now = Instant.now();
        PromoCode promoCode = PromoCode.builder()
            .id(UUID.randomUUID())
            .code(normalizedCode)
            .discountType(type)
            .value(value)
            .validFrom(validFrom)
            .validUntil(validUntil)
            .usageCap(usageCap)
            .usageCount(0)
            .firstTimeOnly(firstTimeOnlyValue)
            .active(activeValue)
            .createdAt(now).updatedAt(now)
            .createdBy(actorId).updatedBy(actorId)
            .build();

        PromoCode saved = promoCodeRepository.save(promoCode);
        log.info("Promo code created id={} code={}", saved.getId(), saved.getCode());
        return saved;
    }

    // ── updatePromoCode ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public PromoCode updatePromoCode(UUID actorId, UUID id, DiscountType type, BigDecimal value,
                                      LocalDate validFrom, LocalDate validUntil, Integer usageCap,
                                      Boolean firstTimeOnly, Boolean active) {
        PromoCode existing = promoCodeRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("promo_code.not_found id={}", id);
                return new ResourceNotFoundException(
                    ErrorCode.PROMO_CODE_NOT_FOUND, "Promo code not found: " + id);
            });

        // Compute effective type and value for cross-field validation
        DiscountType effectiveType  = type  != null ? type  : existing.getDiscountType();
        BigDecimal   effectiveValue = value != null ? value : existing.getValue();

        // BR-3: re-validate if either type or value changed
        if (type != null || value != null) {
            validateValue(effectiveType, effectiveValue);
        }

        // Compute effective dates for cross-field validation
        LocalDate effectiveFrom  = validFrom  != null ? validFrom  : existing.getValidFrom();
        LocalDate effectiveUntil = validUntil != null ? validUntil : existing.getValidUntil();

        // BR-4: re-validate if either date changed
        if (validFrom != null || validUntil != null) {
            validateDateRange(effectiveFrom, effectiveUntil);
        }

        PromoCode updated = PromoCode.builder()
            // BR-6: immutable fields always carried forward
            .id(existing.getId())
            .version(existing.getVersion())
            .code(existing.getCode())
            .usageCount(existing.getUsageCount())
            // Mutable fields — null argument means keep existing
            .discountType(effectiveType)
            .value(effectiveValue)
            .validFrom(effectiveFrom)
            .validUntil(effectiveUntil)
            .usageCap(usageCap != null ? usageCap : existing.getUsageCap())
            .firstTimeOnly(firstTimeOnly != null ? firstTimeOnly : existing.getFirstTimeOnly())
            .active(active != null ? active : existing.getActive())
            // Audit — createdAt/createdBy preserved; updatedAt/updatedBy refreshed (BR-7)
            .createdAt(existing.getCreatedAt())
            .createdBy(existing.getCreatedBy())
            .updatedAt(Instant.now())
            .updatedBy(actorId)
            .build();

        PromoCode saved = promoCodeRepository.save(updated);
        log.info("Promo code updated id={}", saved.getId());
        return saved;
    }

    // ── getPromoCode ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PromoCode getPromoCode(UUID id) {
        log.debug("promo_code.get id={}", id);
        return promoCodeRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("promo_code.not_found id={}", id);
                return new ResourceNotFoundException(
                    ErrorCode.PROMO_CODE_NOT_FOUND, "Promo code not found: " + id);
            });
    }

    // ── getPromoCodeByCode ────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<PromoCode> getPromoCodeByCode(String code) {
        return promoCodeRepository.findByCode(code);
    }

    // ── listPromoCodes ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<PromoCode> listPromoCodes(Boolean active, DiscountType type) {
        log.debug("promo_code.list active={} type={}", active, type);
        return promoCodeRepository.findAll().stream()
            .filter(p -> active == null || p.getActive().equals(active))
            .filter(p -> type   == null || p.getDiscountType() == type)
            .toList();
    }

    // ── deletePromoCode ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deletePromoCode(UUID actorId, UUID id) {
        promoCodeRepository.softDelete(id, actorId);
        log.info("Promo code soft-deleted id={}", id);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * BR-3: validates discount value against discount type.
     * <ul>
     *   <li>PERCENTAGE: must be > 0 and <= 100</li>
     *   <li>FLAT: must be > 0</li>
     * </ul>
     */
    private static void validateValue(DiscountType type, BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Discount value must be greater than zero.");
        }
        if (type == DiscountType.PERCENTAGE && value.compareTo(new BigDecimal("100")) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Percentage discount value must not exceed 100.");
        }
    }

    /** BR-4: validates that validUntil is not before validFrom. */
    private static void validateDateRange(LocalDate validFrom, LocalDate validUntil) {
        if (validUntil.isBefore(validFrom)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "validUntil must not be before validFrom.");
        }
    }
}
