package io.genfin.api.id;

public final class EventId extends Identifier {

  private EventId(String value) {
    super(value);
  }

  public static EventId of(String value) {
    return new EventId(value);
  }

  public static EventId generate() {
    return new EventId(IdentifierGenerators.uuidV4().generate());
  }
}
