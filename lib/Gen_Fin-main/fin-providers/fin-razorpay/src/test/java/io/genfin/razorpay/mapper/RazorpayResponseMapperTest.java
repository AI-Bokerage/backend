package io.genfin.razorpay.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.razorpay.Order;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class RazorpayResponseMapperTest {

  @Test
  void mapsEntityIdIntoGatewayReference() {
    Order order = new Order(new JSONObject().put("id", "order_ABC123"));

    var response = RazorpayResponseMapper.fromEntity(order);

    assertThat(response.success()).isTrue();
    assertThat(response.reference()).contains("order_ABC123");
  }
}
