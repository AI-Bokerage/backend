package io.genfin.providerapi.tokenization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class PaymentTokenTest {

  @Test
  void eachTokenKindCarriesItsValue() {
    assertThat(new VaultToken("v1").value()).isEqualTo("v1");
    assertThat(new CustomerToken("c1").value()).isEqualTo("c1");
    assertThat(new CardToken("card1").value()).isEqualTo("card1");
    assertThat(new NetworkToken("net1").value()).isEqualTo("net1");
  }

  @Test
  void blankTokenValueIsRejected() {
    assertThatThrownBy(() -> new CardToken(" ")).isInstanceOf(ValidationException.class);
  }
}
