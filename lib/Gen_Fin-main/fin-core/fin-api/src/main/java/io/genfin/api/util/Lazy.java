package io.genfin.api.util;

import java.util.function.Supplier;

/** A value computed at most once, on first access, thread-safely. */
public final class Lazy<T> {

  private volatile Supplier<T> supplier;
  private T value;

  private Lazy(Supplier<T> supplier) {
    this.supplier = supplier;
  }

  public static <T> Lazy<T> of(Supplier<T> supplier) {
    return new Lazy<>(supplier);
  }

  public T get() {
    Supplier<T> localSupplier = supplier;
    if (localSupplier != null) {
      synchronized (this) {
        localSupplier = supplier;
        if (localSupplier != null) {
          value = localSupplier.get();
          supplier = null;
        }
      }
    }
    return value;
  }

  public boolean isEvaluated() {
    return supplier == null;
  }
}
