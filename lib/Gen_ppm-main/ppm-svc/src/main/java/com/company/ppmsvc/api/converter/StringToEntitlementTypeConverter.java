package com.company.ppmsvc.api.converter;

import com.company.ppmsvc.entitlement.model.EntitlementType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converts HTTP query-parameter strings to {@link EntitlementType} using the
 * enum's wire values ("boolean", "quota", "rate_limit") rather than the constant
 * names ("BOOLEAN", "QUOTA", "RATE_LIMIT").
 *
 * <p>Spring Boot auto-registers any {@code @Component} that implements
 * {@link Converter} with the application's {@link org.springframework.core.convert.ConversionService},
 * so this is picked up without any explicit {@code WebMvcConfigurer} registration.
 *
 * <p>An unrecognised value causes {@link EntitlementType#fromValue} to throw
 * {@link IllegalArgumentException}, which Spring MVC wraps as a
 * {@link org.springframework.web.method.annotation.MethodArgumentTypeMismatchException}
 * → 400 Bad Request.
 */
@Component
public class StringToEntitlementTypeConverter implements Converter<String, EntitlementType> {

    @Override
    public EntitlementType convert(String source) {
        return EntitlementType.fromValue(source);
    }
}
