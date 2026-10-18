package com.company.ppmsvc.api.converter;

import com.company.ppmsvc.promotion.model.EligibilityType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converts HTTP query-parameter strings to {@link EligibilityType} using the
 * enum's wire values ("new_customer", "existing_customer") rather than the
 * constant names.
 *
 * <p>Spring Boot auto-registers any {@code @Component} {@link Converter} with
 * the application's {@link org.springframework.core.convert.ConversionService},
 * so this is picked up without any explicit {@code WebMvcConfigurer} registration.
 */
@Component
public class StringToEligibilityTypeConverter implements Converter<String, EligibilityType> {

    @Override
    public EligibilityType convert(String source) {
        return EligibilityType.fromValue(source);
    }
}
