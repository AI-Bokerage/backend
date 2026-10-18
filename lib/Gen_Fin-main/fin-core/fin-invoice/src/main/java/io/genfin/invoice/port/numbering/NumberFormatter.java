package io.genfin.invoice.port.numbering;

import io.genfin.api.port.spi.Extension;
import io.genfin.invoice.numbering.NumberTemplate;
import java.util.Map;

/** Renders a {@link NumberTemplate} against a token map into the final number text. */
public interface NumberFormatter extends Extension {

  String format(NumberTemplate template, Map<String, String> tokens);
}
