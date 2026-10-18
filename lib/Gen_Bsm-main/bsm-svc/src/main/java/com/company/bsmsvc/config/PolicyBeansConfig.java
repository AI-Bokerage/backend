package com.company.bsmsvc.config;

import com.company.bsmsvc.domain.model.DunningPolicy;
import com.company.bsmsvc.domain.model.ReconciliationPolicy;
import com.company.bsmsvc.domain.model.TrialPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bridges host-bound {@code @ConfigurationProperties} into the plain domain
 * value objects that library (bsm-core) application services depend on.
 */
@Configuration
public class PolicyBeansConfig {

    @Bean
    public ReconciliationPolicy reconciliationPolicy(ReconciliationProperties properties) {
        return new ReconciliationPolicy(properties.thresholdSeconds());
    }

    @Bean
    public DunningPolicy dunningPolicy(DunningProperties properties) {
        DunningProperties.Policy policy = properties.policy();
        return new DunningPolicy(
            policy.day1RetryAfterHours(),
            policy.day3RetryAfterHours(),
            policy.day7RetryAfterHours(),
            policy.suspendAfterDays(),
            policy.cancelAfterDays());
    }

    @Bean
    public TrialPolicy trialPolicy(@Value("${bsm.trial.default-days:14}") int defaultTrialDays) {
        return new TrialPolicy(defaultTrialDays);
    }
}
