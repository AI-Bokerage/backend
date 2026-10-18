package io.genfin.api.result;

import io.genfin.api.exception.ErrorCode;
import java.util.function.Consumer;
import java.util.function.Function;

public record Failure<T>(ErrorCode errorCode, String message, Throwable cause)
    implements Result<T> {

  @Override
  public boolean isSuccess() {
    return false;
  }

  @Override
  public T get() {
    throw new IllegalStateException("Cannot get value of a Failure: " + message, cause);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> Result<R> map(Function<? super T, ? extends R> mapper) {
    return (Result<R>) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> Result<R> flatMap(Function<? super T, ? extends Result<R>> mapper) {
    return (Result<R>) this;
  }

  @Override
  public Result<T> recover(Function<? super Failure<T>, ? extends T> recovery) {
    return Result.success(recovery.apply(this));
  }

  @Override
  public <R> R fold(
      Function<? super T, ? extends R> onSuccess,
      Function<? super Failure<T>, ? extends R> onFailure) {
    return onFailure.apply(this);
  }

  @Override
  public Result<T> onSuccess(Consumer<? super T> action) {
    return this;
  }

  @Override
  public Result<T> onFailure(Consumer<? super Failure<T>> action) {
    action.accept(this);
    return this;
  }
}
