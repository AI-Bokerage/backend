package io.genfin.api.serialization;

import io.genfin.api.exception.SerializationException;

public interface Serializer<T> {

  byte[] serialize(T value) throws SerializationException;

  SerializationFormat format();
}
