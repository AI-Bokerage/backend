package com.company.bsmsvc.starter.config.validation;

import com.company.bsmsvc.starter.validation.BsmPortAvailabilityValidator;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "bsm", name = "enabled", matchIfMissing = true)
public class BsmPortAvailabilityValidatorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "bsmPortAvailabilityValidator")
    public SmartInitializingSingleton bsmPortAvailabilityValidator(ApplicationContext context) {
        return new BsmPortAvailabilityValidator(context);
    }
}
