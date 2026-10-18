package io.genfin.stripe.compliance;

import io.genfin.payment.port.gateway.PaymentGateway;
import io.genfin.providerapi.compliance.PaymentGatewayComplianceTest;
import io.genfin.stripe.gateway.StripeGateway;
import io.genfin.stripe.support.TestFixtures;

class StripeGatewayComplianceTest extends PaymentGatewayComplianceTest {

  @Override
  protected PaymentGateway gateway() {
    return new StripeGateway(TestFixtures.stripeConfiguration());
  }
}
