package io.genfin.providerapi.descriptor;

/** Higher values are preferred by selection strategies. */
public record ProviderPriority(int value) implements Comparable<ProviderPriority> {

  public static final ProviderPriority DEFAULT = new ProviderPriority(0);

  @Override
  public int compareTo(ProviderPriority other) {
    return Integer.compare(value, other.value);
  }
}
