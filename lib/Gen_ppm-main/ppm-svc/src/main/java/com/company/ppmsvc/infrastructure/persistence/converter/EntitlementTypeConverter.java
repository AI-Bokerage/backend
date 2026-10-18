package com.company.ppmsvc.infrastructure.persistence.converter;

import com.company.ppmsvc.entitlement.model.EntitlementType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA {@link AttributeConverter} that stores {@link EntitlementType} as its stable
 * wire value (e.g. {@code "boolean"}, {@code "quota"}, {@code "rate_limit"}) rather
 * than the Java enum constant name.
 *
 * <p>This allows the Java constant to be renamed without a schema migration
 * and keeps the DB values human-readable.
 */
@Converter(autoApply = true)
public class EntitlementTypeConverter implements AttributeConverter<EntitlementType, String> {

    @Override
    public String convertToDatabaseColumn(EntitlementType attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public EntitlementType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EntitlementType.fromValue(dbData);
    }
}
