package com.company.ppmsvc.api.converter;

import com.company.ppmsvc.campaign.model.CampaignStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converts HTTP query-parameter strings to {@link CampaignStatus} using the
 * enum's wire values rather than the constant names. Auto-registered — see
 * {@link StringToPromotionStatusConverter} for the pattern.
 */
@Component
public class StringToCampaignStatusConverter implements Converter<String, CampaignStatus> {

    @Override
    public CampaignStatus convert(String source) {
        return CampaignStatus.fromValue(source);
    }
}
