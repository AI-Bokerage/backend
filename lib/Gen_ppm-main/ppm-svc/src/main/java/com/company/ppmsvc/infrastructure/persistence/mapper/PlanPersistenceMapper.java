package com.company.ppmsvc.infrastructure.persistence.mapper;

import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.infrastructure.persistence.entity.PlanEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the {@link Plan} domain model and
 * {@link PlanEntity} JPA entity.
 *
 * <p>MapStruct resolves inherited fields from {@code JpaBaseEntity}
 * ({@code @MappedSuperclass}) automatically — no explicit mappings are
 * needed for {@code id}, {@code version}, or the audit columns.
 *
 * <p>{@code deletedAt} lives on the entity (soft-delete marker) but has no
 * counterpart on the domain model; it is ignored in both mapping directions.
 * The {@code domainEvents} list is transient and skipped automatically.
 */
@Mapper(componentModel = "spring")
public interface PlanPersistenceMapper {

    /** Converts a {@link PlanEntity} (from the DB) into a {@link Plan} domain model. */
    Plan toDomain(PlanEntity entity);

    /**
     * Converts a {@link Plan} domain model into a {@link PlanEntity}
     * ready for persistence.
     *
     * <p>{@code deletedAt} is not present on the domain model and must be set
     * by the adapter when performing a soft-delete.
     */
    @Mapping(target = "deletedAt", ignore = true)
    PlanEntity toEntity(Plan plan);

    /** Bulk conversion — used when loading the full plan catalog. */
    List<Plan> toDomainList(List<PlanEntity> entities);
}
