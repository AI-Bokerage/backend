package io.genfin.api.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CollectionUtils {

  private CollectionUtils() {}

  public static <T> List<T> immutableList(Collection<? extends T> source) {
    return source == null ? List.of() : List.copyOf(source);
  }

  public static <T> Set<T> immutableSet(Collection<? extends T> source) {
    return source == null ? Set.of() : Set.copyOf(source);
  }

  public static <K, V> Map<K, V> immutableMap(Map<? extends K, ? extends V> source) {
    return source == null ? Map.of() : Map.copyOf(source);
  }

  public static boolean isEmpty(Collection<?> collection) {
    return collection == null || collection.isEmpty();
  }
}
