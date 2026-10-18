package com.company.ppmsvc.infrastructure.persistence.mapper;

import com.company.ppmsvc.planmodule.model.PlanModule;
import com.company.ppmsvc.infrastructure.persistence.entity.PlanModuleEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper between the {@link PlanModule} domain model and
 * {@link PlanModuleEntity} JPA entity.
 *
 * <p>{@code unmappedSourcePolicy = IGNORE} suppresses warnings for source
 * properties that have no counterpart in the target:
 * <ul>
 *   <li>{@code PlanModule.updatedAt} / {@code updatedBy} — these fields exist
 *       on {@code AuditableEntity} but the mapping table has no equivalent
 *       columns (rows are create-only).</li>
 *   <li>{@code BaseEntity.domainEvents} — transient, never persisted.</li>
 * </ul>
 *
 * <p>All other fields ({@code id}, {@code version}, {@code planId},
 * {@code moduleId}, {@code createdAt}, {@code createdBy}) match by name and
 * are mapped automatically.
 */
@Mapper(componentModel = "spring", unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface PlanModulePersistenceMapper {

    /** Converts a {@link PlanModuleEntity} (from DB) into a {@link PlanModule} domain model. */
    PlanModule toDomain(PlanModuleEntity entity);

    /** Converts a {@link PlanModule} domain model into a {@link PlanModuleEntity} for persistence. */
    PlanModuleEntity toEntity(PlanModule domain);

    /** Bulk conversion — used when loading all mappings for a plan. */
    List<PlanModule> toDomainList(List<PlanModuleEntity> entities);
}
