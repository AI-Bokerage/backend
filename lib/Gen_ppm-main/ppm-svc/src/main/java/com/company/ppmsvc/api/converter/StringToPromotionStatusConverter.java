package com.company.ppmsvc.api.converter;

import com.company.ppmsvc.promotion.model.PromotionStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converts HTTP query-parameter strings to {@link PromotionStatus} using the
 * enum's wire values ("active", "inactive", "draft") rather than the constant
 * names.
 *
 * <p>Spring Boot auto-registers any {@code @Component} {@link Converter} with
 * the application's {@link org.springframework.core.convert.ConversionService},
 * so this is picked up without any explicit {@code WebMvcConfigurer} registration.
 *
 * <p>An unrecognised value causes {@link PromotionStatus#fromValue} to throw
 * {@link IllegalArgumentException}, which Spring MVC wraps as a 400 Bad Request.
 */
@Component
public class StringToPromotionStatusConverter implements Converter<String, PromotionStatus> {

    @Override
    public PromotionStatus convert(String source) {
        return PromotionStatus.fromValue(source);
    }
}
