package com.company.bsmsvc;

import com.company.bsmsvc.config.RazorpayProperties;
import com.company.bsmsvc.config.StripeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({StripeProperties.class, RazorpayProperties.class,
    com.company.bsmsvc.config.WebhookProperties.class, com.company.bsmsvc.config.ReconciliationProperties.class,
    com.company.bsmsvc.config.DunningProperties.class, com.company.bsmsvc.config.BsmSecurityProperties.class})
public class BsmSvcApplication {

    public static void main(String[] args) {
        SpringApplication.run(BsmSvcApplication.class, args);
    }
}
