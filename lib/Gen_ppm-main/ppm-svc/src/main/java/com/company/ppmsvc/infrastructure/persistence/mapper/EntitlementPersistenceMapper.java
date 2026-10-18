package com.company.ppmsvc.infrastructure.persistence.mapper;

import com.company.ppmsvc.entitlement.model.Entitlement;
import com.company.ppmsvc.infrastructure.persistence.entity.EntitlementEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the {@link Entitlement} domain model and
 * {@link EntitlementEntity} JPA entity.
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
public interface EntitlementPersistenceMapper {

    /** Converts an {@link EntitlementEntity} (from the DB) into an {@link Entitlement} domain model. */
    Entitlement toDomain(EntitlementEntity entity);

    /**
     * Converts an {@link Entitlement} domain model into an {@link EntitlementEntity}
     * ready for persistence.
     *
     * <p>{@code deletedAt} is not present on the domain model; it must be set
     * by the adapter when performing a soft-delete.
     */
    @Mapping(target = "deletedAt", ignore = true)
    EntitlementEntity toEntity(Entitlement entitlement);

    /** Bulk conversion — used when loading the full entitlement catalog. */
    List<Entitlement> toDomainList(List<EntitlementEntity> entities);
}
