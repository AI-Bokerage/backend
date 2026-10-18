package com.company.ppmsvc.infrastructure.persistence.converter;

import com.company.ppmsvc.addon.model.AddOnType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA {@link AttributeConverter} that stores {@link AddOnType} as its stable
 * wire value (e.g. {@code "quota"}) rather than the Java enum constant name.
 *
 * <p>This allows the Java constant to be renamed without a schema migration
 * and keeps the DB values human-readable.
 */
@Converter(autoApply = true)
public class AddOnTypeConverter implements AttributeConverter<AddOnType, String> {

    @Override
    public String convertToDatabaseColumn(AddOnType attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public AddOnType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AddOnType.fromValue(dbData);
    }
}
