package io.genfin.invoice.internal.metadata;

import io.genfin.invoice.metadata.Metadata;
import io.genfin.invoice.metadata.TypedValue;
import io.genfin.invoice.port.metadata.MetadataResolver;
import java.util.Optional;

public final class DefaultMetadataResolver implements MetadataResolver {

  @Override
  public Optional<TypedValue> resolve(Metadata metadata, String key) {
    return metadata.find(key);
  }
}
