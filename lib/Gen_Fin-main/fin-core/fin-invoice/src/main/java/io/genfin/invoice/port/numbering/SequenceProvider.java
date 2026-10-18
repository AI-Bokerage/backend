package io.genfin.invoice.port.numbering;

import io.genfin.api.port.spi.Extension;

/** Source of monotonically increasing numbers, partitioned by an opaque {@code sequenceKey}. */
public interface SequenceProvider extends Extension {

  long next(String sequenceKey);
}
