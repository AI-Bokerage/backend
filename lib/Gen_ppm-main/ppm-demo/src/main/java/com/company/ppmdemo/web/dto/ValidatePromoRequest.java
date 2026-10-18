package com.company.ppmdemo.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ValidatePromoRequest(
    @NotBlank String code,
    @NotNull UUID planId
) {}
