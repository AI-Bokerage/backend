package com.company.ppmsvc.api.dto.request;

import com.company.ppmsvc.promotion.model.ReferralCodeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateReferralCodeRequest(

    @NotBlank
    String code,

    @NotNull
    UUID referralProgramId,

    @NotBlank
    String referrerCustomerId,

    ReferralCodeStatus status
) {}
