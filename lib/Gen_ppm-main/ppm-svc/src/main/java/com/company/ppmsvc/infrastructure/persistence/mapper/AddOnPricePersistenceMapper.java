package com.company.ppmsvc.infrastructure.persistence.mapper;

import com.company.ppmsvc.addonprice.model.AddOnPrice;
import com.company.ppmsvc.infrastructure.persistence.entity.AddOnPriceEntity;
import java.util.List;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper between the {@link AddOnPrice} domain model and
 * {@link AddOnPriceEntity} JPA entity.
 *
 * <p>{@code toDomain} and {@code toEntity} are implemented as {@code default}
 * methods rather than abstract methods because MapStruct 1.6.x misidentifies
 * the {@code addOnId(UUID)} builder method as a collection adder
 * ({@code add} + {@code OnId}) and silently omits the field from auto-mapping.
 * The manual implementations guarantee every field — including {@code addOnId}
 * — is mapped in both directions.  MapStruct still generates {@code toDomainList}.
 */
@Mapper(componentModel = "spring")
public interface AddOnPricePersistenceMapper {

    /** Converts an {@link AddOnPriceEntity} (from the DB) into an {@link AddOnPrice} domain model. */
    default AddOnPrice toDomain(AddOnPriceEntity entity) {
        if (entity == null) {
            return null;
        }
        return AddOnPrice.builder()
                .id(entity.getId())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .addOnId(entity.getAddOnId())
                .cycle(entity.getCycle())
                .currency(entity.getCurrency())
                .region(entity.getRegion())
                .amount(entity.getAmount())
                .taxInclusive(entity.isTaxInclusive())
                .effectiveFrom(entity.getEffectiveFrom())
                .active(entity.isActive())
                .build();
    }

    /**
     * Converts an {@link AddOnPrice} domain model into an {@link AddOnPriceEntity}
     * ready for persistence.  {@code deletedAt} is intentionally omitted — it is
     * set only by the adapter during a soft-delete operation.
     */
    default AddOnPriceEntity toEntity(AddOnPrice price) {
        if (price == null) {
            return null;
        }
        return AddOnPriceEntity.builder()
                .id(price.getId())
                .version(price.getVersion())
                .createdAt(price.getCreatedAt())
                .updatedAt(price.getUpdatedAt())
                .createdBy(price.getCreatedBy())
                .updatedBy(price.getUpdatedBy())
                .addOnId(price.getAddOnId())
                .cycle(price.getCycle())
                .currency(price.getCurrency())
                .region(price.getRegion())
                .amount(price.getAmount())
                .taxInclusive(price.isTaxInclusive())
                .effectiveFrom(price.getEffectiveFrom())
                .active(price.isActive())
                .build();
    }

    /** Bulk conversion — used when loading all prices for an add-on. */
    List<AddOnPrice> toDomainList(List<AddOnPriceEntity> entities);
}
