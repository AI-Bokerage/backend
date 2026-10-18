package com.company.ppmsvc.infrastructure.persistence.mapper;

import com.company.ppmsvc.plan.model.PlanVersion;
import com.company.ppmsvc.infrastructure.persistence.entity.PlanVersionEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the {@link PlanVersion} domain model and
 * {@link PlanVersionEntity} JPA entity.
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
public interface PlanVersionPersistenceMapper {

    /** Converts a {@link PlanVersionEntity} (from the DB) into a {@link PlanVersion} domain model. */
    PlanVersion toDomain(PlanVersionEntity entity);

    /**
     * Converts a {@link PlanVersion} domain model into a {@link PlanVersionEntity}
     * ready for persistence.
     *
     * <p>{@code deletedAt} is not present on the domain model; it must be set
     * by the adapter when performing a soft-delete.
     */
    @Mapping(target = "deletedAt", ignore = true)
    PlanVersionEntity toEntity(PlanVersion planVersion);

    /** Bulk conversion — used when loading the full version list. */
    List<PlanVersion> toDomainList(List<PlanVersionEntity> entities);
}
