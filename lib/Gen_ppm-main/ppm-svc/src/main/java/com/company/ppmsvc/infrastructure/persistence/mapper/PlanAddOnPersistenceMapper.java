package com.company.ppmsvc.infrastructure.persistence.mapper;

import com.company.ppmsvc.planaddon.model.PlanAddOn;
import com.company.ppmsvc.infrastructure.persistence.entity.PlanAddOnEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper between the {@link PlanAddOn} domain model and
 * {@link PlanAddOnEntity} JPA entity.
 *
 * <p>{@code toDomain} and {@code toEntity} are implemented as {@code default}
 * methods rather than abstract methods because MapStruct 1.6.x misidentifies
 * the {@code addOnId(UUID)} builder method as a collection adder
 * ({@code add} + {@code OnId}) and silently omits the field from auto-mapping.
 * The manual implementations guarantee every field — including {@code addOnId}
 * — is mapped in both directions.  MapStruct still generates {@code toDomainList}.
 *
 * <p>{@code unmappedSourcePolicy = IGNORE} suppresses warnings for
 * {@code updatedAt} / {@code updatedBy} which exist on {@code AuditableEntity}
 * but have no equivalent columns on this create-only join table.
 */
@Mapper(componentModel = "spring", unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface PlanAddOnPersistenceMapper {

    /** Converts a {@link PlanAddOnEntity} (from DB) into a {@link PlanAddOn} domain model. */
    default PlanAddOn toDomain(PlanAddOnEntity entity) {
        if (entity == null) {
            return null;
        }
        return PlanAddOn.builder()
                .id(entity.getId())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .createdBy(entity.getCreatedBy())
                .planId(entity.getPlanId())
                .addOnId(entity.getAddOnId())
                .build();
    }

    /** Converts a {@link PlanAddOn} domain model into a {@link PlanAddOnEntity} for persistence. */
    default PlanAddOnEntity toEntity(PlanAddOn domain) {
        if (domain == null) {
            return null;
        }
        return PlanAddOnEntity.builder()
                .id(domain.getId())
                .version(domain.getVersion())
                .planId(domain.getPlanId())
                .addOnId(domain.getAddOnId())
                .createdAt(domain.getCreatedAt())
                .createdBy(domain.getCreatedBy())
                .build();
    }

    /** Bulk conversion — used when loading all assignments for a plan. */
    List<PlanAddOn> toDomainList(List<PlanAddOnEntity> entities);
}
