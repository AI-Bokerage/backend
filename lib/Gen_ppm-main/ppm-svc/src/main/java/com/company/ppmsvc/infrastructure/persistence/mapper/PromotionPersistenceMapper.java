package com.company.ppmsvc.infrastructure.persistence.mapper;

import com.company.ppmsvc.infrastructure.persistence.entity.PromotionEntity;
import com.company.ppmsvc.promotion.model.Promotion;
import com.company.ppmsvc.promotion.model.PromotionAction;
import com.company.ppmsvc.promotion.model.PromotionCondition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * MapStruct mapper between the {@link Promotion} domain model and {@link
 * PromotionEntity} JPA entity.
 *
 * <p>An abstract class (not an interface) so it can hold the injected {@link
 * ObjectMapper} used to convert the polymorphic {@code action} and {@code
 * conditions} fields to/from JSONB. MapStruct discovers {@link #toAction} /
 * {@link #fromAction} and {@link #toConditions} / {@link #fromConditions} by
 * exact type match — no explicit {@code @Mapping} is needed for those fields.
 * {@code status} is converted via {@code PromotionStatus.fromValue}/{@code
 * getValue}.
 */
@Mapper(componentModel = "spring")
public abstract class PromotionPersistenceMapper {

    private static final TypeReference<List<PromotionCondition>> CONDITIONS_TYPE = new TypeReference<>() {};

    @Autowired
    protected ObjectMapper objectMapper;

    /** Converts a {@link PromotionEntity} (from the DB) into a {@link Promotion} domain model. */
    @Mapping(target = "status", expression = "java(com.company.ppmsvc.promotion.model.PromotionStatus.fromValue(entity.getStatus()))")
    @Mapping(target = "source", expression = "java(com.company.ppmsvc.promotion.model.PromotionSource.fromValue(entity.getSource()))")
    public abstract Promotion toDomain(PromotionEntity entity);

    /**
     * Converts a {@link Promotion} domain model into a {@link PromotionEntity}
     * ready for persistence. {@code source} is never null on the domain side
     * (the builder defaults it), so this never writes a null column value.
     */
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "status", expression = "java(promotion.getStatus().getValue())")
    @Mapping(target = "source", expression = "java(promotion.getSource().getValue())")
    public abstract PromotionEntity toEntity(Promotion promotion);

    /** Bulk conversion — used when loading the full promotion catalog. */
    public abstract List<Promotion> toDomainList(List<PromotionEntity> entities);

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

    public List<PromotionCondition> toConditions(JsonNode node) {
        try {
            return objectMapper.convertValue(node, CONDITIONS_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid conditions JSON", e);
        }
    }

    /**
     * {@code objectMapper.valueToTree(conditions)} would serialize using the
     * list's erased runtime type and never emit the {@code "type"}
     * discriminator per element. Forcing the declared {@code
     * List<PromotionCondition>} {@link com.fasterxml.jackson.databind.JavaType}
     * via a typed {@code ObjectWriter} makes Jackson apply polymorphic type
     * resolution for each element, matching how {@link #toConditions} reads it back.
     */
    public JsonNode fromConditions(List<PromotionCondition> conditions) {
        try {
            var listType = objectMapper.getTypeFactory().constructType(CONDITIONS_TYPE);
            String json = objectMapper.writerFor(listType).writeValueAsString(conditions != null ? conditions : List.of());
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid conditions", e);
        }
    }
}
