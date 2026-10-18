package io.genfin.money.format;

public record FormattingPolicy(FormattingContext defaultContext) {

  public static FormattingPolicy of(FormattingContext defaultContext) {
    return new FormattingPolicy(defaultContext);
  }
}
