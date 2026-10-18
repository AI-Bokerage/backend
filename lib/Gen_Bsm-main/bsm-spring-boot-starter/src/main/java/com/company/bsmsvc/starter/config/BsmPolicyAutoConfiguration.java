package com.company.bsmsvc.starter.config;

import com.company.bsmsvc.domain.model.DunningPolicy;
import com.company.bsmsvc.domain.model.ReconciliationPolicy;
import com.company.bsmsvc.domain.model.TrialPolicy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Bridges {@link BsmProperties} into the plain domain policy value objects bsm-core depends on. */
@AutoConfiguration
@ConditionalOnProperty(prefix = "bsm", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(BsmProperties.class)
public class BsmPolicyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DunningPolicy dunningPolicy(BsmProperties properties) {
        BsmProperties.Dunning d = properties.getDunning();
        return new DunningPolicy(
            d.getDay1RetryAfterHours(), d.getDay3RetryAfterHours(), d.getDay7RetryAfterHours(),
            d.getSuspendAfterDays(), d.getCancelAfterDays());
    }

    @Bean
    @ConditionalOnMissingBean
    public TrialPolicy trialPolicy(BsmProperties properties) {
        return new TrialPolicy(properties.getTrial().getDefaultDays());
    }

    @Bean
    @ConditionalOnMissingBean
    public ReconciliationPolicy reconciliationPolicy(BsmProperties properties) {
        return new ReconciliationPolicy(properties.getReconciliation().getThresholdSeconds());
    }
}
