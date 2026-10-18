package com.company.ppmsvc.infrastructure.persistence.converter;

import com.company.ppmsvc.module.model.ModuleCode;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA {@link AttributeConverter} that stores {@link ModuleCode} as its stable
 * wire value (e.g. {@code "lead_management"}) rather than the Java enum
 * constant name.
 *
 * <p>This allows the Java constant to be renamed without a schema migration
 * and keeps the DB values human-readable.
 */
@Converter(autoApply = true)
public class ModuleCodeConverter implements AttributeConverter<ModuleCode, String> {

    @Override
    public String convertToDatabaseColumn(ModuleCode attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ModuleCode convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ModuleCode.fromValue(dbData);
    }
}
