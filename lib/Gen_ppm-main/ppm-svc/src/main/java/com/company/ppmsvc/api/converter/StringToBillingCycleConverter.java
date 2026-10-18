package com.company.ppmsvc.api.converter;

import com.company.ppmsvc.common.BillingCycle;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converts HTTP query-parameter strings to {@link BillingCycle} using the
 * enum's wire values ("monthly", "annual") rather than the constant names
 * ("MONTHLY", "ANNUAL").
 *
 * <p>Spring Boot auto-registers any {@code @Component} that implements
 * {@link Converter} with the application's {@link org.springframework.core.convert.ConversionService},
 * so this is picked up without any explicit {@code WebMvcConfigurer} registration.
 *
 * <p>An unrecognised value causes {@link BillingCycle#fromValue} to throw
 * {@link IllegalArgumentException}, which Spring MVC wraps as a
 * {@link org.springframework.web.method.annotation.MethodArgumentTypeMismatchException}
 * → 400 Bad Request.
 */
@Component
public class StringToBillingCycleConverter implements Converter<String, BillingCycle> {

    @Override
    public BillingCycle convert(String source) {
        return BillingCycle.fromValue(source);
    }
}
