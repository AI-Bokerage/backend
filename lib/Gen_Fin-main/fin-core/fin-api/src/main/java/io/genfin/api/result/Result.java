package io.genfin.api.result;

import io.genfin.api.exception.ErrorCode;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Explicit success/failure outcome of an operation. Prefer this over throwing for expected failure
 * paths, and over {@code Optional<T>} when the reason for absence matters.
 */
public sealed interface Result<T> permits Success, Failure {

  static <T> Result<T> success(T value) {
    return new Success<>(value);
  }

  static <T> Result<T> failure(ErrorCode errorCode, String message) {
    return new Failure<>(errorCode, message, null);
  }

  static <T> Result<T> failure(ErrorCode errorCode, String message, Throwable cause) {
    return new Failure<>(errorCode, message, cause);
  }

  boolean isSuccess();

  default boolean isFailure() {
    return !isSuccess();
  }

  T get();

  default T getOrElse(T fallback) {
    return isSuccess() ? get() : fallback;
  }

  <R> Result<R> map(Function<? super T, ? extends R> mapper);

  <R> Result<R> flatMap(Function<? super T, ? extends Result<R>> mapper);

  Result<T> recover(Function<? super Failure<T>, ? extends T> recovery);

  <R> R fold(
      Function<? super T, ? extends R> onSuccess,
      Function<? super Failure<T>, ? extends R> onFailure);

  Result<T> onSuccess(Consumer<? super T> action);

  Result<T> onFailure(Consumer<? super Failure<T>> action);
}
