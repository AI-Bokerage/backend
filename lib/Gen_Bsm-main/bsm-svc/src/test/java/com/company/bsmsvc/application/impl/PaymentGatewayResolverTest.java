package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;
import com.company.bsmsvc.infrastructure.gateway.RazorpayPaymentAdapter;
import com.company.bsmsvc.infrastructure.gateway.StripePaymentAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayResolverTest {

    @Mock private StripePaymentAdapter stripeAdapter;
    @Mock private RazorpayPaymentAdapter razorpayAdapter;

    private PaymentGatewayResolverImpl resolver;

    @BeforeEach
    void setUp() {
        resolver = new PaymentGatewayResolverImpl(stripeAdapter, razorpayAdapter);
    }

    @Test
    void resolve_returnsStripeAdapter_forStripe() {
        PaymentGatewayPort result = resolver.resolve(PaymentProvider.STRIPE);
        assertThat(result).isSameAs(stripeAdapter);
    }

    @Test
    void resolve_returnsRazorpayAdapter_forRazorpay() {
        PaymentGatewayPort result = resolver.resolve(PaymentProvider.RAZORPAY);
        assertThat(result).isSameAs(razorpayAdapter);
    }

    @Test
    void resolve_stripe_isNotRazorpay() {
        assertThat(resolver.resolve(PaymentProvider.STRIPE)).isNotSameAs(razorpayAdapter);
    }

    @Test
    void resolve_razorpay_isNotStripe() {
        assertThat(resolver.resolve(PaymentProvider.RAZORPAY)).isNotSameAs(stripeAdapter);
    }
}
