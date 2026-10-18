package com.example.bsmdemo.adapter;

import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;
import org.springframework.stereotype.Component;

/** Demo resolver — always returns the single in-memory gateway regardless of provider. */
@Component
public class DemoPaymentGatewayResolver implements PaymentGatewayResolver {

    private final DemoPaymentGatewayAdapter gateway;

    public DemoPaymentGatewayResolver(DemoPaymentGatewayAdapter gateway) {
        this.gateway = gateway;
    }

    @Override
    public PaymentGatewayPort resolve(PaymentProvider provider) {
        return gateway;
    }
}
