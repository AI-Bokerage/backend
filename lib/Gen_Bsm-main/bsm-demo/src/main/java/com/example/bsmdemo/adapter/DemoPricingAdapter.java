package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.model.PpmResolvePriceResult;
import com.company.bsmsvc.domain.port.PpmPricingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Hardcoded price resolution — stands in for a real PPM-SVC pricing call. */
@Component
public class DemoPricingAdapter implements PpmPricingService {

    @Override
    public PpmResolvePriceResult resolvePrice(UUID ppmPlanId, String region, String currency, String cycle) {
        return new PpmResolvePriceResult(
            ppmPlanId,
            UUID.randomUUID(),
            cycle,
            currency,
            region,
            new BigDecimal("29.00"),
            false,
            LocalDate.of(2024, 1, 1),
            true,
            1L
        );
    }
}
