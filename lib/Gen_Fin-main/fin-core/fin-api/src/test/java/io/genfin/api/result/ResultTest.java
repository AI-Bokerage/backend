package io.genfin.api.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.CoreErrorCode;
import org.junit.jupiter.api.Test;

class ResultTest {

  @Test
  void successMapsAndFoldsToOnSuccessBranch() {
    Result<Integer> result = Result.success(2);

    assertThat(result.isSuccess()).isTrue();
    Result<Integer> doubled = result.map(v -> v * 2);
    assertThat(doubled.get()).isEqualTo(4);
    String folded = result.fold(v -> "ok:" + v, f -> "fail");
    assertThat(folded).isEqualTo("ok:2");
  }

  @Test
  void failurePropagatesThroughMapAndFlatMap() {
    Result<Integer> result = Result.failure(CoreErrorCode.VALIDATION_FAILED, "bad");

    assertThat(result.isFailure()).isTrue();
    assertThat(result.map(v -> v * 2).isFailure()).isTrue();
    assertThat(result.flatMap(v -> Result.success(v * 2)).isFailure()).isTrue();
  }

  @Test
  void recoverTurnsFailureIntoSuccess() {
    Result<Integer> result = Result.<Integer>failure(CoreErrorCode.UNKNOWN, "bad").recover(f -> -1);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get()).isEqualTo(-1);
  }

  @Test
  void gettingFailureValueThrows() {
    Result<Integer> result = Result.failure(CoreErrorCode.UNKNOWN, "bad");

    assertThatThrownBy(result::get).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void onSuccessAndOnFailureCallbacksFireExclusively() {
    int[] hits = new int[2];
    Result.success(1).onSuccess(v -> hits[0]++).onFailure(f -> hits[1]++);
    Result.failure(CoreErrorCode.UNKNOWN, "x").onSuccess(v -> hits[0]++).onFailure(f -> hits[1]++);

    assertThat(hits).containsExactly(1, 1);
  }
}
