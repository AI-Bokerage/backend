package io.genfin.api.serialization;

import io.genfin.api.exception.SerializationException;

public interface Deserializer<T> {

  T deserialize(byte[] data) throws SerializationException;

  SerializationFormat format();
}
