package com.company.ppmsvc.referral.usecase;

import com.company.ppmsvc.referral.model.ReferralReward;

/**
 * Records a referral conversion and grants the referrer's reward.
 *
 * <p>Called by checkout on successful subscription — PPM does not detect
 * conversion on its own.
 */
public interface ReferralConversionService {

    /**
     * Records that {@code referredCustomerId} converted via {@code referralCode},
     * grants the referrer's reward, and delivers it (currently via a one-time coupon).
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code REFERRAL_CODE_NOT_FOUND} if the code does not exist, or
     *         {@code REFERRAL_PROGRAM_NOT_FOUND} if its program does not exist.
     * @throws com.company.ppmsvc.exception.BusinessException with
     *         {@code REFERRAL_ALREADY_CONVERTED} if this (code, customer) pair already
     *         converted, or {@code REFERRAL_CAP_REACHED} if the referrer's program cap
     *         is reached.
     */
    ReferralReward onConversion(String referralCode, String referredCustomerId);
}
