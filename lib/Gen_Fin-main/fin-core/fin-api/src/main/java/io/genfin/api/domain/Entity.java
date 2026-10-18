package io.genfin.api.domain;

import io.genfin.api.validation.Validate;
import java.util.Objects;

/** Base class for objects with identity: equality is by id, regardless of attribute values. */
public abstract class Entity<ID> implements DomainObject {

  private final ID id;

  protected Entity(ID id) {
    this.id = Validate.notNull(id, "Entity id must not be null.");
  }

  public final ID id() {
    return id;
  }

  @Override
  public final boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Entity<?> entity)) {
      return false;
    }
    return getClass() == entity.getClass() && id.equals(entity.id);
  }

  @Override
  public final int hashCode() {
    return Objects.hash(getClass(), id);
  }
}
