package com.company.ppmsvc.infrastructure.persistence.mapper;

import com.company.ppmsvc.planprice.model.PlanPrice;
import com.company.ppmsvc.infrastructure.persistence.entity.PlanPriceEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the {@link PlanPrice} domain model and
 * {@link PlanPriceEntity} JPA entity.
 *
 * <p>MapStruct resolves inherited fields from {@code JpaBaseEntity}
 * ({@code @MappedSuperclass}) automatically — no explicit mappings are
 * needed for {@code id}, {@code version}, or the audit columns.
 *
 * <p>{@code deletedAt} lives on the entity (soft-delete marker) but has no
 * counterpart on the domain model; it is ignored when converting to entity.
 * The {@code domainEvents} list is transient and skipped automatically.
 */
@Mapper(componentModel = "spring")
public interface PlanPricePersistenceMapper {

    /** Converts a {@link PlanPriceEntity} (from the DB) into a {@link PlanPrice} domain model. */
    PlanPrice toDomain(PlanPriceEntity entity);

    /**
     * Converts a {@link PlanPrice} domain model into a {@link PlanPriceEntity}
     * ready for persistence.
     *
     * <p>{@code deletedAt} is not present on the domain model; it must be set
     * by the adapter when performing a soft-delete.
     */
    @Mapping(target = "deletedAt", ignore = true)
    PlanPriceEntity toEntity(PlanPrice price);

    /** Bulk conversion — used when loading the full price catalog. */
    List<PlanPrice> toDomainList(List<PlanPriceEntity> entities);
}
