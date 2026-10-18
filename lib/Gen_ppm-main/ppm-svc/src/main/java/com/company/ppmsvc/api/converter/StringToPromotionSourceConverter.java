package com.company.ppmsvc.api.converter;

import com.company.ppmsvc.promotion.model.PromotionSource;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converts HTTP query-parameter strings to {@link PromotionSource} using the
 * enum's wire values rather than the constant names. Auto-registered — see
 * {@link StringToPromotionStatusConverter} for the pattern.
 */
@Component
public class StringToPromotionSourceConverter implements Converter<String, PromotionSource> {

    @Override
    public PromotionSource convert(String source) {
        return PromotionSource.fromValue(source);
    }
}
