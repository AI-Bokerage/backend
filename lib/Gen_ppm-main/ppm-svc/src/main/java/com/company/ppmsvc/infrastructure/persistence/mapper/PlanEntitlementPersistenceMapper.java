package com.company.ppmsvc.infrastructure.persistence.mapper;

import com.company.ppmsvc.planentitlement.model.PlanEntitlement;
import com.company.ppmsvc.infrastructure.persistence.entity.PlanEntitlementEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper between the {@link PlanEntitlement} domain model and
 * {@link PlanEntitlementEntity} JPA entity.
 *
 * <p>{@code unmappedSourcePolicy = IGNORE} suppresses warnings for source
 * properties that have no counterpart in the target:
 * <ul>
 *   <li>{@code PlanEntitlement.updatedAt} / {@code updatedBy} — these fields exist
 *       on {@code AuditableEntity} but the assignment table has no equivalent
 *       columns (rows are create-only).</li>
 *   <li>{@code BaseEntity.domainEvents} — transient, never persisted.</li>
 * </ul>
 *
 * <p>All other fields ({@code id}, {@code version}, {@code planId},
 * {@code entitlementId}, {@code value}, {@code createdAt}, {@code createdBy})
 * match by name and are mapped automatically.
 */
@Mapper(componentModel = "spring", unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface PlanEntitlementPersistenceMapper {

    /** Converts a {@link PlanEntitlementEntity} (from DB) into a {@link PlanEntitlement} domain model. */
    PlanEntitlement toDomain(PlanEntitlementEntity entity);

    /** Converts a {@link PlanEntitlement} domain model into a {@link PlanEntitlementEntity} for persistence. */
    PlanEntitlementEntity toEntity(PlanEntitlement domain);

    /** Bulk conversion — used when loading all assignments for a plan. */
    List<PlanEntitlement> toDomainList(List<PlanEntitlementEntity> entities);
}
