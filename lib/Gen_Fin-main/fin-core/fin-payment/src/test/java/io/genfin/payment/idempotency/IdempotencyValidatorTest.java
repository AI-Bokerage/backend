package io.genfin.payment.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class IdempotencyValidatorTest {

  @Test
  void noExistingFingerprintIsNew() {
    var result =
        IdempotencyValidators.standard().validate(IdempotencyKey.of("k1"), "fp1", Optional.empty());

    assertThat(result.isNew()).isTrue();
  }

  @Test
  void matchingFingerprintIsAReplay() {
    var result =
        IdempotencyValidators.standard()
            .validate(IdempotencyKey.of("k1"), "fp1", Optional.of("fp1"));

    assertThat(result.isReplay()).isTrue();
  }

  @Test
  void differentFingerprintIsAConflict() {
    var result =
        IdempotencyValidators.standard()
            .validate(IdempotencyKey.of("k1"), "fp2", Optional.of("fp1"));

    assertThat(result.isConflict()).isTrue();
  }
}
