package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;
import com.company.bsmsvc.infrastructure.gateway.RazorpayPaymentAdapter;
import com.company.bsmsvc.infrastructure.gateway.StripePaymentAdapter;
import org.springframework.stereotype.Component;

@Component
public class PaymentGatewayResolverImpl implements PaymentGatewayResolver {

    private final StripePaymentAdapter stripeAdapter;
    private final RazorpayPaymentAdapter razorpayAdapter;

    public PaymentGatewayResolverImpl(StripePaymentAdapter stripeAdapter,
                                      RazorpayPaymentAdapter razorpayAdapter) {
        this.stripeAdapter = stripeAdapter;
        this.razorpayAdapter = razorpayAdapter;
    }

    @Override
    public PaymentGatewayPort resolve(PaymentProvider provider) {
        return switch (provider) {
            case STRIPE -> stripeAdapter;
            case RAZORPAY -> razorpayAdapter;
        };
    }
}
