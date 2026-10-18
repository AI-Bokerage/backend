package com.company.bsmsvc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.bsmsvc.domain.enums.PpmPlanChangeType;
import com.company.bsmsvc.domain.model.PpmProrationResult;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PpmProrationEngineTest {

    private final PpmProrationEngine engine = new PpmProrationEngine();

    // Scenario: 30-day period, change happens exactly halfway through.
    // current = 1000 minor, target = 2000 minor
    // fraction = 0.5 → credit = 500, charge = 1000, net = 500 (UPGRADE)
    @Test
    void upgradeAtHalfwayReturnsCorrectProration() {
        Instant start  = Instant.parse("2026-06-01T00:00:00Z");
        Instant end    = Instant.parse("2026-07-01T00:00:00Z");
        Instant now    = Instant.parse("2026-06-16T00:00:00Z");

        PpmProrationResult result = engine.calculate(1000L, 2000L, start, end, now);

        assertThat(result.changeType()).isEqualTo(PpmPlanChangeType.UPGRADE);
        assertThat(result.creditAmountMinor()).isEqualTo(500L);
        assertThat(result.chargeAmountMinor()).isEqualTo(1000L);
        assertThat(result.netAmountMinor()).isEqualTo(500L);
        assertThat(result.fraction()).isBetween(BigDecimal.valueOf(0.49), BigDecimal.valueOf(0.51));
    }

    @Test
    void downgradeAtHalfwayReturnsCorrectProration() {
        Instant start  = Instant.parse("2026-06-01T00:00:00Z");
        Instant end    = Instant.parse("2026-07-01T00:00:00Z");
        Instant now    = Instant.parse("2026-06-16T00:00:00Z");

        PpmProrationResult result = engine.calculate(2000L, 1000L, start, end, now);

        assertThat(result.changeType()).isEqualTo(PpmPlanChangeType.DOWNGRADE);
        assertThat(result.creditAmountMinor()).isEqualTo(1000L);
        assertThat(result.chargeAmountMinor()).isEqualTo(500L);
        assertThat(result.netAmountMinor()).isEqualTo(-500L);
    }

    @Test
    void samePriceReturnsPlanSwitchWithZeroNet() {
        Instant start  = Instant.parse("2026-06-01T00:00:00Z");
        Instant end    = Instant.parse("2026-07-01T00:00:00Z");
        Instant now    = Instant.parse("2026-06-16T00:00:00Z");

        PpmProrationResult result = engine.calculate(1500L, 1500L, start, end, now);

        assertThat(result.changeType()).isEqualTo(PpmPlanChangeType.PLAN_SWITCH);
        assertThat(result.netAmountMinor()).isZero();
    }

    @Test
    void changeAtEndOfPeriodReturnsZeroProration() {
        Instant start  = Instant.parse("2026-06-01T00:00:00Z");
        Instant end    = Instant.parse("2026-07-01T00:00:00Z");
        // now is after period end — no remaining time
        Instant now    = Instant.parse("2026-07-01T00:00:01Z");

        PpmProrationResult result = engine.calculate(1000L, 2000L, start, end, now);

        assertThat(result.creditAmountMinor()).isZero();
        assertThat(result.chargeAmountMinor()).isZero();
        assertThat(result.netAmountMinor()).isZero();
        assertThat(result.fraction()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void changeAtStartOfPeriodReturnsFullProration() {
        Instant start  = Instant.parse("2026-06-01T00:00:00Z");
        Instant end    = Instant.parse("2026-07-01T00:00:00Z");
        Instant now    = Instant.parse("2026-06-01T00:00:00Z");

        PpmProrationResult result = engine.calculate(1000L, 2000L, start, end, now);

        // fraction ≈ 1.0 → credit ≈ 1000, charge ≈ 2000, net ≈ 1000
        assertThat(result.creditAmountMinor()).isEqualTo(1000L);
        assertThat(result.chargeAmountMinor()).isEqualTo(2000L);
        assertThat(result.netAmountMinor()).isEqualTo(1000L);
    }

    @Test
    void zeroPeriodLengthReturnsFractionZero() {
        Instant sameInstant = Instant.parse("2026-06-15T12:00:00Z");

        PpmProrationResult result = engine.calculate(1000L, 2000L, sameInstant, sameInstant, sameInstant);

        assertThat(result.fraction()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.netAmountMinor()).isZero();
    }

    @Test
    void fractionNeverExceedsOne() {
        Instant start = Instant.parse("2026-06-15T00:00:00Z");
        Instant end   = Instant.parse("2026-06-15T01:00:00Z"); // 1 hour period
        // now is before the period start — remaining > total
        Instant now   = Instant.parse("2026-06-14T00:00:00Z");

        PpmProrationResult result = engine.calculate(1000L, 2000L, start, end, now);

        // remaining seconds > total seconds → clamped to total → fraction = 1
        assertThat(result.creditAmountMinor()).isEqualTo(1000L);
        assertThat(result.chargeAmountMinor()).isEqualTo(2000L);
    }
}
