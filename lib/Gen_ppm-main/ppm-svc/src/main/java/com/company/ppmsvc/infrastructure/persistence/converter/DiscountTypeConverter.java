package com.company.ppmsvc.infrastructure.persistence.converter;

import com.company.ppmsvc.promocode.model.DiscountType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA {@link AttributeConverter} that stores {@link DiscountType} as its stable
 * wire value (e.g. {@code "percentage"}, {@code "flat"}) rather than the Java
 * enum constant name.
 *
 * <p>This allows the Java constant to be renamed without a schema migration
 * and keeps the DB values human-readable.
 */
@Converter(autoApply = true)
public class DiscountTypeConverter implements AttributeConverter<DiscountType, String> {

    @Override
    public String convertToDatabaseColumn(DiscountType attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public DiscountType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : DiscountType.fromValue(dbData);
    }
}
