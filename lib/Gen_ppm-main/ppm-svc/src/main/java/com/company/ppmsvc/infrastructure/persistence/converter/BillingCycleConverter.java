package com.company.ppmsvc.infrastructure.persistence.converter;

import com.company.ppmsvc.common.BillingCycle;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA {@link AttributeConverter} that stores {@link BillingCycle} as its stable
 * wire value (e.g. {@code "monthly"}, {@code "annual"}) rather than the Java
 * enum constant name.
 *
 * <p>This allows the Java constant to be renamed without a schema migration
 * and keeps the DB values human-readable.
 */
@Converter(autoApply = true)
public class BillingCycleConverter implements AttributeConverter<BillingCycle, String> {

    @Override
    public String convertToDatabaseColumn(BillingCycle attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public BillingCycle convertToEntityAttribute(String dbData) {
        return dbData == null ? null : BillingCycle.fromValue(dbData);
    }
}
