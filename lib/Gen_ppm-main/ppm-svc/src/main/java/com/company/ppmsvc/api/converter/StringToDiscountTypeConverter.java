package com.company.ppmsvc.api.converter;

import com.company.ppmsvc.promocode.model.DiscountType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converts HTTP query-parameter strings to {@link DiscountType} using the
 * enum's wire values ("percentage", "flat") rather than the constant names.
 *
 * <p>Spring Boot auto-registers any {@code @Component} {@link Converter} with
 * the application's {@link org.springframework.core.convert.ConversionService}.
 *
 * <p>An unrecognised value causes {@link DiscountType#fromValue} to throw
 * {@link IllegalArgumentException}, which Spring MVC wraps as a 400 Bad Request.
 */
@Component
public class StringToDiscountTypeConverter implements Converter<String, DiscountType> {

    @Override
    public DiscountType convert(String source) {
        return DiscountType.fromValue(source);
    }
}
