package com.company.ppmsvc.api.dto.request;

import com.company.ppmsvc.promotion.model.ReferralCodeStatus;

/** {@code code} is intentionally absent — immutable after creation. */
public record UpdateReferralCodeRequest(

    ReferralCodeStatus status
) {}
