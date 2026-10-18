package com.company.bsmsvc.domain.service;

import com.company.bsmsvc.domain.enums.PpmPlanChangeType;
import com.company.bsmsvc.domain.model.PpmProrationResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * Proration calculator for PPM-backed plan changes.
 *
 * <p>Uses BigDecimal throughout — {@code double} is explicitly forbidden to avoid
 * rounding errors accumulating across multi-period or high-value subscriptions.
 * {@link com.company.bsmsvc.domain.service.proration.FlatProrationStrategy} is left
 * unchanged; this engine handles only PPM-originated change events.
 *
 * <p>Algorithm:
 * <pre>
 *   fraction = max(0, secondsRemaining) / totalSeconds
 *   credit   = currentPriceMinor  × fraction   (HALF_UP, 0 dp)
 *   charge   = targetPriceMinor   × fraction   (HALF_UP, 0 dp)
 *   net      = charge - credit
 * </pre>
 *
 * <p>Change type is determined purely by price comparison:
 * <ul>
 *   <li>targetPrice > currentPrice → UPGRADE</li>
 *   <li>targetPrice < currentPrice → DOWNGRADE</li>
 *   <li>equal → PLAN_SWITCH</li>
 * </ul>
 */
@Component
public class PpmProrationEngine {

    private static final int FRACTION_SCALE = 10;

    /**
     * @param currentPriceMinor  locked price from subscription (ppmResolvedPriceMinor)
     * @param targetPriceMinor   newly resolved price for the target plan
     * @param periodStart        current billing period start (inclusive)
     * @param periodEnd          current billing period end (exclusive)
     * @param now                the instant at which the change is being processed
     */
    public PpmProrationResult calculate(
        long currentPriceMinor,
        long targetPriceMinor,
        Instant periodStart,
        Instant periodEnd,
        Instant now
    ) {
        long totalSecs     = ChronoUnit.SECONDS.between(periodStart, periodEnd);
        long remainingSecs = ChronoUnit.SECONDS.between(now, periodEnd);

        BigDecimal fraction;
        if (totalSecs <= 0) {
            fraction = BigDecimal.ZERO;
        } else {
            long effectiveRemaining = Math.max(0L, Math.min(remainingSecs, totalSecs));
            fraction = BigDecimal.valueOf(effectiveRemaining)
                .divide(BigDecimal.valueOf(totalSecs), FRACTION_SCALE, RoundingMode.HALF_UP);
        }

        long credit = BigDecimal.valueOf(currentPriceMinor)
            .multiply(fraction)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact();

        long charge = BigDecimal.valueOf(targetPriceMinor)
            .multiply(fraction)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact();

        long net = charge - credit;

        PpmPlanChangeType changeType = resolveChangeType(currentPriceMinor, targetPriceMinor);

        return new PpmProrationResult(credit, charge, net, fraction, changeType);
    }

    private static PpmPlanChangeType resolveChangeType(long current, long target) {
        if (target > current) return PpmPlanChangeType.UPGRADE;
        if (target < current) return PpmPlanChangeType.DOWNGRADE;
        return PpmPlanChangeType.PLAN_SWITCH;
    }
}
