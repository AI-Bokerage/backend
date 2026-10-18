package io.genfin.invoice.port.metadata;

import io.genfin.api.port.spi.Extension;
import io.genfin.invoice.metadata.Metadata;
import io.genfin.invoice.metadata.TypedValue;
import java.util.Optional;

public interface MetadataResolver extends Extension {

  Optional<TypedValue> resolve(Metadata metadata, String key);
}
