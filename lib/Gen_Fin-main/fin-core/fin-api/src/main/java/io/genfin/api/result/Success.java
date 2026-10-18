package io.genfin.api.result;

import java.util.function.Consumer;
import java.util.function.Function;

public record Success<T>(T value) implements Result<T> {

  @Override
  public boolean isSuccess() {
    return true;
  }

  @Override
  public T get() {
    return value;
  }

  @Override
  public <R> Result<R> map(Function<? super T, ? extends R> mapper) {
    return Result.success(mapper.apply(value));
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> Result<R> flatMap(Function<? super T, ? extends Result<R>> mapper) {
    return (Result<R>) mapper.apply(value);
  }

  @Override
  public Result<T> recover(Function<? super Failure<T>, ? extends T> recovery) {
    return this;
  }

  @Override
  public <R> R fold(
      Function<? super T, ? extends R> onSuccess,
      Function<? super Failure<T>, ? extends R> onFailure) {
    return onSuccess.apply(value);
  }

  @Override
  public Result<T> onSuccess(Consumer<? super T> action) {
    action.accept(value);
    return this;
  }

  @Override
  public Result<T> onFailure(Consumer<? super Failure<T>> action) {
    return this;
  }
}
