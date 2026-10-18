package io.genfin.api.id;

public final class AggregateId extends Identifier {

  private AggregateId(String value) {
    super(value);
  }

  public static AggregateId of(String value) {
    return new AggregateId(value);
  }
}
