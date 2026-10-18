package com.company.ppmsvc.api.converter;

import com.company.ppmsvc.plan.model.PlanVisibility;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converts HTTP query-parameter strings to {@link PlanVisibility} using the
 * enum's wire values ("public", "private", "legacy") rather than the constant
 * names ("PUBLIC", "PRIVATE", "LEGACY").
 *
 * <p>Spring Boot auto-registers any {@code @Component} that implements
 * {@link Converter} with the application's {@link org.springframework.core.convert.ConversionService},
 * so this is picked up without any explicit {@code WebMvcConfigurer} registration.
 *
 * <p>An unrecognised value causes {@link PlanVisibility#fromValue} to throw
 * {@link IllegalArgumentException}, which Spring MVC wraps as a
 * {@link org.springframework.web.method.annotation.MethodArgumentTypeMismatchException}
 * → 400 Bad Request.
 */
@Component
public class StringToPlanVisibilityConverter implements Converter<String, PlanVisibility> {

    @Override
    public PlanVisibility convert(String source) {
        return PlanVisibility.fromValue(source);
    }
}
