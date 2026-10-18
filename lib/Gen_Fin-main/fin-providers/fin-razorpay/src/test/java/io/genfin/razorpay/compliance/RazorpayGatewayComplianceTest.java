package io.genfin.razorpay.compliance;

import io.genfin.payment.port.gateway.PaymentGateway;
import io.genfin.providerapi.compliance.PaymentGatewayComplianceTest;
import io.genfin.razorpay.gateway.RazorpayGateway;
import io.genfin.razorpay.support.TestFixtures;

class RazorpayGatewayComplianceTest extends PaymentGatewayComplianceTest {

  @Override
  protected PaymentGateway gateway() {
    return new RazorpayGateway(TestFixtures.razorpayConfiguration());
  }
}
