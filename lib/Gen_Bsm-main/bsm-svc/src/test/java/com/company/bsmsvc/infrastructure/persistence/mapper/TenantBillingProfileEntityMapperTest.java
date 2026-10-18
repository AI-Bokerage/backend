package com.company.bsmsvc.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.infrastructure.persistence.entity.TenantBillingProfileEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantBillingProfileEntityMapperTest {

    private final TenantBillingProfileEntityMapper mapper = new TenantBillingProfileEntityMapper();

    @Test
    void toEntity_preservesNullVersionAsNull() {
        TenantBillingProfile domain = TenantBillingProfile.builder()
            .id(UUID.randomUUID())
            .tenantId(UUID.randomUUID())
            .paymentProvider(PaymentProvider.STRIPE)
            .externalCustomerId("cus_test")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .version(null)
            .build();

        TenantBillingProfileEntity entity = mapper.toEntity(domain);

        assertThat(entity.getVersion()).isNull();
    }
}
