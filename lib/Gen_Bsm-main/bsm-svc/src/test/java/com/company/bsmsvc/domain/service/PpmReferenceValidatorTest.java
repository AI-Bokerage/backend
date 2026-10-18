package com.company.bsmsvc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.bsmsvc.domain.enums.PpmSnapshotStatus;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.model.Subscription;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PpmReferenceValidatorTest {

    private final PpmReferenceValidator validator = new PpmReferenceValidator();

    private static final UUID PLAN_ID    = UUID.randomUUID();
    private static final UUID PRICE_ID   = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();

    // -----------------------------------------------------------------------
    // check()
    // -----------------------------------------------------------------------

    @Test
    void check_allNull_returnsNotPpmBacked() {
        Subscription sub = Subscription.builder().build();

        assertThat(validator.check(sub)).isEqualTo(PpmSnapshotStatus.NOT_PPM_BACKED);
    }

    @Test
    void check_allPopulated_returnsHealthy() {
        Subscription sub = Subscription.builder()
            .ppmPlanId(PLAN_ID)
            .ppmPriceId(PRICE_ID)
            .ppmPlanVersionId(VERSION_ID)
            .ppmResolvedPriceMinor(99900L)
            .build();

        assertThat(validator.check(sub)).isEqualTo(PpmSnapshotStatus.HEALTHY);
    }

    @Test
    void check_onlyPlanIdPopulated_returnsPartialReference() {
        Subscription sub = Subscription.builder()
            .ppmPlanId(PLAN_ID)
            .build();

        assertThat(validator.check(sub)).isEqualTo(PpmSnapshotStatus.PARTIAL_REFERENCE);
    }

    @Test
    void check_threePpmFieldsPopulated_returnsPartialReference() {
        Subscription sub = Subscription.builder()
            .ppmPlanId(PLAN_ID)
            .ppmPriceId(PRICE_ID)
            .ppmPlanVersionId(VERSION_ID)
            // ppmResolvedPriceMinor intentionally omitted
            .build();

        assertThat(validator.check(sub)).isEqualTo(PpmSnapshotStatus.PARTIAL_REFERENCE);
    }

    // -----------------------------------------------------------------------
    // isPpmBacked()
    // -----------------------------------------------------------------------

    @Test
    void isPpmBacked_ppmPlanIdNull_returnsFalse() {
        assertThat(validator.isPpmBacked(Subscription.builder().build())).isFalse();
    }

    @Test
    void isPpmBacked_ppmPlanIdPopulated_returnsTrue() {
        Subscription sub = Subscription.builder().ppmPlanId(PLAN_ID).build();
        assertThat(validator.isPpmBacked(sub)).isTrue();
    }

    // -----------------------------------------------------------------------
    // isPartial()
    // -----------------------------------------------------------------------

    @Test
    void isPartial_allNull_returnsFalse() {
        assertThat(validator.isPartial(Subscription.builder().build())).isFalse();
    }

    @Test
    void isPartial_allPopulated_returnsFalse() {
        Subscription sub = Subscription.builder()
            .ppmPlanId(PLAN_ID)
            .ppmPriceId(PRICE_ID)
            .ppmPlanVersionId(VERSION_ID)
            .ppmResolvedPriceMinor(50000L)
            .build();
        assertThat(validator.isPartial(sub)).isFalse();
    }

    @Test
    void isPartial_twoOfFourPopulated_returnsTrue() {
        Subscription sub = Subscription.builder()
            .ppmPlanId(PLAN_ID)
            .ppmPriceId(PRICE_ID)
            .build();
        assertThat(validator.isPartial(sub)).isTrue();
    }

    // -----------------------------------------------------------------------
    // validateOrThrow()
    // -----------------------------------------------------------------------

    @Test
    void validateOrThrow_allNull_doesNotThrow() {
        validator.validateOrThrow(Subscription.builder().build());
    }

    @Test
    void validateOrThrow_allPopulated_doesNotThrow() {
        Subscription sub = Subscription.builder()
            .ppmPlanId(PLAN_ID)
            .ppmPriceId(PRICE_ID)
            .ppmPlanVersionId(VERSION_ID)
            .ppmResolvedPriceMinor(50000L)
            .build();
        validator.validateOrThrow(sub);
    }

    @Test
    void validateOrThrow_partialReference_throwsBusinessRuleViolation() {
        Subscription sub = Subscription.builder()
            .ppmPlanId(PLAN_ID)
            .build();

        assertThatThrownBy(() -> validator.validateOrThrow(sub))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("inconsistent PPM reference");
    }
}
