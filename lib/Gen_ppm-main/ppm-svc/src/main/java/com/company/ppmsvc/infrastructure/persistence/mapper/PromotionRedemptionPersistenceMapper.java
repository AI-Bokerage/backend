package com.company.ppmsvc.infrastructure.persistence.mapper;

import com.company.ppmsvc.infrastructure.persistence.entity.PromotionRedemptionEntity;
import com.company.ppmsvc.promotion.model.PromotionAction;
import com.company.ppmsvc.promotionredemption.model.PromotionRedemption;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * MapStruct mapper between the {@link PromotionRedemption} domain model and
 * {@link PromotionRedemptionEntity} JPA entity. Abstract class (not an
 * interface) so it can hold the injected {@link ObjectMapper} used to convert
 * {@code appliedAction} to/from JSONB — same pattern as {@link
 * PromotionPersistenceMapper}.
 */
@Mapper(componentModel = "spring")
public abstract class PromotionRedemptionPersistenceMapper {

    @Autowired
    protected ObjectMapper objectMapper;

    public abstract PromotionRedemption toDomain(PromotionRedemptionEntity entity);

    @Mapping(target = "deletedAt", ignore = true)
    public abstract PromotionRedemptionEntity toEntity(PromotionRedemption redemption);

    public abstract List<PromotionRedemption> toDomainList(List<PromotionRedemptionEntity> entities);

    public PromotionAction toAction(JsonNode node) {
        try {
            return objectMapper.treeToValue(node, PromotionAction.class);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid action JSON", e);
        }
    }

    public JsonNode fromAction(PromotionAction action) {
        return objectMapper.valueToTree(action);
    }
}
