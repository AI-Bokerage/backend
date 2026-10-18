package com.company.bsmsvc.application.service;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;

/**
 * Resolves the {@link com.company.bsmsvc.domain.port.PaymentGatewayPort} implementation to use
 * for a given payment provider — the strategy lookup that lets {@code bsm-core} stay agnostic to
 * how many gateways a host wires up. Functionally an extension point, though it lives in
 * {@code application.service} rather than {@code domain.port}; see {@code PORT_REFERENCE.md}.
 */
public interface PaymentGatewayResolver {
    PaymentGatewayPort resolve(PaymentProvider provider);
}
