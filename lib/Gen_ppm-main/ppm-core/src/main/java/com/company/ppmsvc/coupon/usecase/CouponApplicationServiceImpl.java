package com.company.ppmsvc.coupon.usecase;

import com.company.ppmsvc.coupon.model.Coupon;
import com.company.ppmsvc.coupon.port.CouponRepositoryPort;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.promotion.port.PromotionRepositoryPort;
import java.time.Instant;
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
public class CouponApplicationServiceImpl implements CouponApplicationService {

    private final CouponRepositoryPort    couponRepository;
    private final PromotionRepositoryPort promotionRepository;

    // ── createCoupon ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Coupon createCoupon(UUID actorId, String code, UUID promotionId, Boolean active) {
        String normalizedCode = code.strip().toUpperCase();

        if (couponRepository.existsByCode(normalizedCode)) {
            log.warn("coupon.conflict code={}", normalizedCode);
            throw new BusinessException(ErrorCode.COUPON_CODE_ALREADY_EXISTS,
                "A coupon with code '" + normalizedCode + "' already exists.");
        }

        requirePromotionExists(promotionId);

        boolean activeValue = active != null ? active : true;

        Instant now = Instant.now();
        Coupon coupon = Coupon.builder()
            .id(UUID.randomUUID())
            .code(normalizedCode)
            .promotionId(promotionId)
            .active(activeValue)
            .createdAt(now).updatedAt(now)
            .createdBy(actorId).updatedBy(actorId)
            .build();

        Coupon saved = couponRepository.save(coupon);
        log.info("Coupon created id={} code={}", saved.getId(), saved.getCode());
        return saved;
    }

    // ── updateCoupon ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Coupon updateCoupon(UUID actorId, UUID id, UUID promotionId, Boolean active) {
        Coupon existing = couponRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("coupon.not_found id={}", id);
                return new ResourceNotFoundException(
                    ErrorCode.COUPON_NOT_FOUND, "Coupon not found: " + id);
            });

        if (promotionId != null) {
            requirePromotionExists(promotionId);
        }

        Coupon updated = Coupon.builder()
            .id(existing.getId())
            .version(existing.getVersion())
            .code(existing.getCode())
            .promotionId(promotionId != null ? promotionId : existing.getPromotionId())
            .active(active != null ? active : existing.getActive())
            .createdAt(existing.getCreatedAt())
            .createdBy(existing.getCreatedBy())
            .updatedAt(Instant.now())
            .updatedBy(actorId)
            .build();

        Coupon saved = couponRepository.save(updated);
        log.info("Coupon updated id={}", saved.getId());
        return saved;
    }

    // ── getCoupon ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Coupon getCoupon(UUID id) {
        log.debug("coupon.get id={}", id);
        return couponRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("coupon.not_found id={}", id);
                return new ResourceNotFoundException(
                    ErrorCode.COUPON_NOT_FOUND, "Coupon not found: " + id);
            });
    }

    // ── getCouponByCode ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<Coupon> getCouponByCode(String code) {
        return couponRepository.findByCode(code);
    }

    // ── listCoupons ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<Coupon> listCoupons(Boolean activeFilter) {
        log.debug("coupon.list active={}", activeFilter);
        return couponRepository.findAll().stream()
            .filter(c -> activeFilter == null || c.getActive().equals(activeFilter))
            .toList();
    }

    // ── deleteCoupon ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteCoupon(UUID actorId, UUID id) {
        couponRepository.softDelete(id, actorId);
        log.info("Coupon soft-deleted id={}", id);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void requirePromotionExists(UUID promotionId) {
        promotionRepository.findById(promotionId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PROMOTION_NOT_FOUND, "Promotion not found: " + promotionId));
    }
}
