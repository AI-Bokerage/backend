package com.company.ppmsvc.api.converter;

import com.company.ppmsvc.addon.model.AddOnType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converts HTTP query-parameter strings to {@link AddOnType} using the
 * enum's wire values ("feature", "quota", "service") rather than the constant names.
 *
 * <p>Spring Boot auto-registers any {@code @Component} {@link Converter} with
 * the application's {@link org.springframework.core.convert.ConversionService}.
 * {@code @JsonCreator} / {@code @JsonValue} are Jackson-only and are not seen
 * by Spring MVC's {@code ConversionService} when binding {@code @RequestParam}.
 *
 * <p>An unrecognised value causes {@link AddOnType#fromValue} to throw
 * {@link IllegalArgumentException}, which Spring MVC wraps as a 400 Bad Request.
 */
@Component
public class StringToAddOnTypeConverter implements Converter<String, AddOnType> {

    @Override
    public AddOnType convert(String source) {
        return AddOnType.fromValue(source);
    }
}
