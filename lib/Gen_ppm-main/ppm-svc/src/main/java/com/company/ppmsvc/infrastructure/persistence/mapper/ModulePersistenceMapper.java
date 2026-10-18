package com.company.ppmsvc.infrastructure.persistence.mapper;

import com.company.ppmsvc.module.model.Module;
import com.company.ppmsvc.infrastructure.persistence.entity.ModuleEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the {@link Module} domain model and
 * {@link ModuleEntity} JPA entity.
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
public interface ModulePersistenceMapper {

    /** Converts a {@link ModuleEntity} (from the DB) into a {@link Module} domain model. */
    Module toDomain(ModuleEntity entity);

    /**
     * Converts a {@link Module} domain model into a {@link ModuleEntity}
     * ready for persistence.
     *
     * <p>{@code deletedAt} is not present on the domain model and must be set
     * by the adapter when performing a soft-delete (not yet needed in Phase 1).
     */
    @Mapping(target = "deletedAt", ignore = true)
    ModuleEntity toEntity(Module module);

    /** Bulk conversion — used when loading the full module catalog. */
    List<Module> toDomainList(List<ModuleEntity> entities);
}
