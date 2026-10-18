package com.company.ppmsvc.infrastructure.persistence.mapper;

import com.company.ppmsvc.addon.model.AddOn;
import com.company.ppmsvc.infrastructure.persistence.entity.AddOnEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the {@link AddOn} domain model and
 * {@link AddOnEntity} JPA entity.
 *
 * <p>MapStruct resolves inherited fields from {@code JpaBaseEntity}
 * ({@code @MappedSuperclass}) automatically — no explicit mappings are
 * needed for {@code id}, {@code version}, or the audit columns.
 *
 * <p>{@code deletedAt} lives on the entity (soft-delete marker) but has no
 * counterpart on the domain model; it is ignored when converting to entity.
 */
@Mapper(componentModel = "spring")
public interface AddOnPersistenceMapper {

    /** Converts an {@link AddOnEntity} (from the DB) into an {@link AddOn} domain model. */
    AddOn toDomain(AddOnEntity entity);

    /**
     * Converts an {@link AddOn} domain model into an {@link AddOnEntity}
     * ready for persistence.
     *
     * <p>{@code deletedAt} is not present on the domain model; it must be set
     * by the adapter when performing a soft-delete.
     */
    @Mapping(target = "deletedAt", ignore = true)
    AddOnEntity toEntity(AddOn addOn);

    /** Bulk conversion — used when loading the full add-on catalog. */
    List<AddOn> toDomainList(List<AddOnEntity> entities);
}
