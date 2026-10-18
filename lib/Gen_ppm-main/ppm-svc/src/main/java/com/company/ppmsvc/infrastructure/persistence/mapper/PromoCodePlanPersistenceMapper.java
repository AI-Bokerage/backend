package com.company.ppmsvc.infrastructure.persistence.mapper;

import com.company.ppmsvc.promocodeplan.model.PromoCodePlan;
import com.company.ppmsvc.infrastructure.persistence.entity.PromoCodePlanEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper between the {@link PromoCodePlan} domain model and
 * {@link PromoCodePlanEntity} JPA entity.
 *
 * <p>{@code unmappedSourcePolicy = IGNORE} suppresses warnings for fields
 * that exist on the domain model but not on the entity — both models are
 * structurally identical for promo-code plan restrictions, but the policy
 * future-proofs against minor divergence.
 *
 * <p>All five fields ({@code id}, {@code promoCodeId}, {@code planId},
 * {@code createdAt}, {@code createdBy}) match by name and are mapped automatically.
 */
@Mapper(componentModel = "spring", unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface PromoCodePlanPersistenceMapper {

    /** Converts a {@link PromoCodePlanEntity} (from DB) into a {@link PromoCodePlan} domain model. */
    PromoCodePlan toDomain(PromoCodePlanEntity entity);

    /** Converts a {@link PromoCodePlan} domain model into a {@link PromoCodePlanEntity} for persistence. */
    PromoCodePlanEntity toEntity(PromoCodePlan domain);

    /** Bulk conversion — used when loading all plan restrictions for a promo code. */
    List<PromoCodePlan> toDomainList(List<PromoCodePlanEntity> entities);
}
