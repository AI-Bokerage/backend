package io.genfin.api.serialization;

/** A paired serializer/deserializer for a single type and format. */
public interface Codec<T> extends Serializer<T>, Deserializer<T> {}
