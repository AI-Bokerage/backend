package com.company.ppmsvc.promotion.model;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AppliedEntitlement.from()")
class AppliedEntitlementTest {

    @Test
    @DisplayName("FreePeriodDiscount -> FREE_PERIOD with null targetId")
    void freePeriod_mapsCorrectly() {
        AppliedEntitlement result = AppliedEntitlement.from(new FreePeriodDiscount(3));

        assertThat(result.type()).isEqualTo(EntitlementType.FREE_PERIOD);
        assertThat(result.targetId()).isNull();
        assertThat(result.durationMonths()).isEqualTo(3);
    }

    @Test
    @DisplayName("FreeModuleDiscount -> FREE_MODULE with moduleId as targetId")
    void freeModule_mapsCorrectly() {
        UUID moduleId = UUID.randomUUID();
        AppliedEntitlement result = AppliedEntitlement.from(new FreeModuleDiscount(moduleId, 6));

        assertThat(result.type()).isEqualTo(EntitlementType.FREE_MODULE);
        assertThat(result.targetId()).isEqualTo(moduleId);
        assertThat(result.durationMonths()).isEqualTo(6);
    }

    @Test
    @DisplayName("FreeAddOnDiscount -> FREE_ADDON with addOnId as targetId")
    void freeAddOn_mapsCorrectly() {
        UUID addOnId = UUID.randomUUID();
        AppliedEntitlement result = AppliedEntitlement.from(new FreeAddOnDiscount(addOnId, 12));

        assertThat(result.type()).isEqualTo(EntitlementType.FREE_ADDON);
        assertThat(result.targetId()).isEqualTo(addOnId);
        assertThat(result.durationMonths()).isEqualTo(12);
    }
}
