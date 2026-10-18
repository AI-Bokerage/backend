package com.company.ppmsvc.infrastructure.persistence.converter;

import com.company.ppmsvc.plan.model.PlanVisibility;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA {@link AttributeConverter} that stores {@link PlanVisibility} as its stable
 * wire value (e.g. {@code "public"}) rather than the Java enum constant name.
 *
 * <p>This allows the Java constant to be renamed without a schema migration
 * and keeps the DB values human-readable.
 */
@Converter(autoApply = true)
public class PlanVisibilityConverter implements AttributeConverter<PlanVisibility, String> {

    @Override
    public String convertToDatabaseColumn(PlanVisibility attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public PlanVisibility convertToEntityAttribute(String dbData) {
        return dbData == null ? null : PlanVisibility.fromValue(dbData);
    }
}
