package com.company.bsmsvc.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.bsmsvc.domain.enums.InvoiceLineItemType;
import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.model.InvoiceLineItem;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.infrastructure.persistence.entity.InvoiceLineItemEntity;
import com.company.bsmsvc.infrastructure.persistence.entity.PlatformInvoiceEntity;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntityMapperTest {

    private PlatformInvoiceEntityMapper invoiceMapper;
    private InvoiceLineItemEntityMapper itemMapper;

    @BeforeEach
    void setUp() throws Exception {
        itemMapper = new InvoiceLineItemEntityMapperImpl();
        invoiceMapper = new PlatformInvoiceEntityMapperImpl();

        // Inject the child mapper dependency into the parent mapper via reflection
        try {
            java.lang.reflect.Field field = PlatformInvoiceEntityMapperImpl.class.getDeclaredField("invoiceLineItemEntityMapper");
            field.setAccessible(true);
            field.set(invoiceMapper, itemMapper);
        } catch (NoSuchFieldException e) {
            // Check if standard MapStruct name is different
            for (java.lang.reflect.Field f : PlatformInvoiceEntityMapperImpl.class.getDeclaredFields()) {
                if (InvoiceLineItemEntityMapper.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    f.set(invoiceMapper, itemMapper);
                }
            }
        }
    }

    @Test
    void testLineItemMapperToEntityAndToDomain() {
        UUID itemId = UUID.randomUUID();
        UUID invId = UUID.randomUUID();
        InvoiceLineItem domain = InvoiceLineItem.builder()
            .id(itemId)
            .invoiceId(invId)
            .itemType(InvoiceLineItemType.SUBSCRIPTION)
            .description("Desc")
            .quantity(1)
            .unitAmountMinor(100L)
            .amountMinor(100L)
            .metadata(new HashMap<>())
            .createdAt(Instant.now())
            .build();

        InvoiceLineItemEntity entity = itemMapper.toEntity(domain);

        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isEqualTo(itemId);
        assertThat(entity.getInvoice()).isNotNull();
        assertThat(entity.getInvoice().getId()).isEqualTo(invId);
        assertThat(entity.getItemType()).isEqualTo(InvoiceLineItemType.SUBSCRIPTION);
        assertThat(entity.getDescription()).isEqualTo("Desc");
        assertThat(entity.getQuantity()).isEqualTo(1);
        assertThat(entity.getUnitAmountMinor()).isEqualTo(100L);
        assertThat(entity.getAmountMinor()).isEqualTo(100L);

        InvoiceLineItem domainFromEntity = itemMapper.toDomain(entity);
        assertThat(domainFromEntity).isNotNull();
        assertThat(domainFromEntity.getId()).isEqualTo(itemId);
        assertThat(domainFromEntity.getInvoiceId()).isEqualTo(invId);
    }

    @Test
    void testPlatformInvoiceMapperToEntityAndToDomain() {
        UUID invoiceId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();

        PlatformInvoice domain = PlatformInvoice.builder()
            .id(invoiceId)
            .tenantId(tenantId)
            .subscriptionId(subId)
            .invoiceNumber("INV-123")
            .status(InvoiceStatus.OPEN)
            .amountDue(500L)
            .amountPaid(100L)
            .currency("INR")
            .periodStart(Instant.now())
            .periodEnd(Instant.now())
            .dueDate(LocalDate.now())
            .paidAt(Instant.now())
            .version(2L)
            .lineItems(new ArrayList<>())
            .build();

        domain.getLineItems().add(InvoiceLineItem.builder()
            .id(UUID.randomUUID())
            .invoiceId(invoiceId)
            .itemType(InvoiceLineItemType.SUBSCRIPTION)
            .description("Service Sub")
            .quantity(1)
            .unitAmountMinor(500L)
            .amountMinor(500L)
            .build());

        PlatformInvoiceEntity entity = invoiceMapper.toEntity(domain);

        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isEqualTo(invoiceId);
        assertThat(entity.getTenantId()).isEqualTo(tenantId);
        assertThat(entity.getSubscription()).isNotNull();
        assertThat(entity.getSubscription().getId()).isEqualTo(subId);
        assertThat(entity.getInvoiceNumber()).isEqualTo("INV-123");
        assertThat(entity.getStatus()).isEqualTo(InvoiceStatus.OPEN);
        assertThat(entity.getAmountDue()).isEqualTo(500L);
        assertThat(entity.getAmountPaid()).isEqualTo(100L);
        assertThat(entity.getCurrency()).isEqualTo("INR");
        assertThat(entity.getVersion()).isEqualTo(2L);
        assertThat(entity.getLineItems()).hasSize(1);
        
        // Assert the after-mapping parent linkage holds true
        assertThat(entity.getLineItems().get(0).getInvoice()).isSameAs(entity);

        PlatformInvoice domainFromEntity = invoiceMapper.toDomain(entity);
        assertThat(domainFromEntity).isNotNull();
        assertThat(domainFromEntity.getId()).isEqualTo(invoiceId);
        assertThat(domainFromEntity.getTenantId()).isEqualTo(tenantId);
        assertThat(domainFromEntity.getSubscriptionId()).isEqualTo(subId);
        assertThat(domainFromEntity.getLineItems()).hasSize(1);
    }
}
