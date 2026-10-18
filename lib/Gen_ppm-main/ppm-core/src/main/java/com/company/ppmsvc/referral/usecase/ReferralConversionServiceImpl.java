package com.company.ppmsvc.referral.usecase;

import com.company.ppmsvc.coupon.model.Coupon;
import com.company.ppmsvc.coupon.port.CouponRepositoryPort;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.promotion.model.ReferralEventStatus;
import com.company.ppmsvc.referral.model.ReferralCode;
import com.company.ppmsvc.referral.model.ReferralEvent;
import com.company.ppmsvc.referral.model.ReferralProgram;
import com.company.ppmsvc.referral.model.ReferralReward;
import com.company.ppmsvc.referral.port.ReferralCodeRepositoryPort;
import com.company.ppmsvc.referral.port.ReferralEventRepositoryPort;
import com.company.ppmsvc.referral.port.ReferralProgramRepositoryPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReferralConversionServiceImpl implements ReferralConversionService {

    private final ReferralCodeRepositoryPort    referralCodeRepository;
    private final ReferralProgramRepositoryPort referralProgramRepository;
    private final ReferralEventRepositoryPort   referralEventRepository;
    private final CouponRepositoryPort          couponRepository;

    @Override
    @Transactional
    public ReferralReward onConversion(String referralCode, String referredCustomerId) {
        ReferralCode code = referralCodeRepository.findByCode(referralCode)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.REFERRAL_CODE_NOT_FOUND, "Referral code not found: " + referralCode));

        ReferralProgram program = referralProgramRepository.findById(code.getReferralProgramId())
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.REFERRAL_PROGRAM_NOT_FOUND, "Referral program not found: " + code.getReferralProgramId()));

        // Idempotency: same (code, referred customer) cannot convert twice.
        Optional<ReferralEvent> existing = referralEventRepository.findByCodeAndCustomer(code.getId(), referredCustomerId);
        if (existing.isPresent() && existing.get().getStatus() == ReferralEventStatus.CONVERTED) {
            log.warn("referral.already_converted code={} customer={}", referralCode, referredCustomerId);
            throw new BusinessException(ErrorCode.REFERRAL_ALREADY_CONVERTED,
                "Referral code '" + referralCode + "' already converted for customer " + referredCustomerId);
        }

        // Cap check
        Integer cap = program.getMaxReferralsPerReferrer();
        if (cap != null && referralEventRepository.countConvertedByCode(code.getId()) >= cap) {
            log.warn("referral.cap_reached code={} programId={}", referralCode, program.getId());
            throw new BusinessException(ErrorCode.REFERRAL_CAP_REACHED,
                "Referrer has reached the maximum referrals for this program.");
        }

        Instant now = Instant.now();
        ReferralEvent event = ReferralEvent.builder()
            .id(UUID.randomUUID())
            .referralCodeId(code.getId())
            .referredCustomerId(referredCustomerId)
            .status(ReferralEventStatus.CONVERTED)
            .convertedAt(now)
            .createdAt(now).updatedAt(now)
            .build();
        event = referralEventRepository.save(event);

        // Step (a): grant the reward — the durable fact.
        event = grantReward(event);

        // Step (b): issue the coupon — the current, swappable delivery mechanism.
        Coupon coupon = issueReferrerCoupon(program, code.getReferrerCustomerId());

        log.info("Referral converted code={} referrer={} referred={} couponCode={}",
            referralCode, code.getReferrerCustomerId(), referredCustomerId, coupon.getCode());

        return new ReferralReward(program.getReferrerRewardPromotionId(), code.getReferrerCustomerId(), coupon.getCode());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Step (a): marks the durable grant fact. Decoupled from however the reward is delivered. */
    private ReferralEvent grantReward(ReferralEvent event) {
        ReferralEvent updated = ReferralEvent.builder()
            .id(event.getId())
            .version(event.getVersion())
            .referralCodeId(event.getReferralCodeId())
            .referredCustomerId(event.getReferredCustomerId())
            .status(event.getStatus())
            .convertedAt(event.getConvertedAt())
            .rewardGrantedAt(Instant.now())
            .createdAt(event.getCreatedAt())
            .createdBy(event.getCreatedBy())
            .updatedAt(Instant.now())
            .updatedBy(event.getUpdatedBy())
            .build();
        return referralEventRepository.save(updated);
    }

    /**
     * Attributed as the actor for coupons issued automatically by this
     * system-triggered flow — {@code onConversion} has no authenticated user
     * to attribute {@code created_by}/{@code updated_by} to, but those
     * columns are {@code NOT NULL} on {@code ppm_coupons}.
     */
    private static final UUID SYSTEM_ACTOR_ID = new UUID(0L, 0L);

    /**
     * Step (b): the current delivery mechanism for a granted reward — a
     * one-time {@link Coupon} pointing at the program's referrer reward
     * promotion. Swappable later (e.g. wallet credit) without changing
     * {@link #grantReward} or the {@link ReferralReward} contract.
     */
    private Coupon issueReferrerCoupon(ReferralProgram program, String referrerCustomerId) {
        String prefix = referrerCustomerId.substring(0, Math.min(8, referrerCustomerId.length())).toUpperCase();
        String code = "REFRR-" + prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Instant now = Instant.now();
        Coupon coupon = Coupon.builder()
            .id(UUID.randomUUID())
            .code(code)
            .promotionId(program.getReferrerRewardPromotionId())
            .active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(SYSTEM_ACTOR_ID).updatedBy(SYSTEM_ACTOR_ID)
            .build();
        return couponRepository.save(coupon);
    }
}
