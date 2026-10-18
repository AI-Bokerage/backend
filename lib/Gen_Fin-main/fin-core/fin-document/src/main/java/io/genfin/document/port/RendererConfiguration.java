package io.genfin.document.port;

import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.result.PdfRenderInputs;
import java.util.Optional;

/** Per-render options passed to a {@link DocumentRenderer}, as an attribute bag. */
public final class RendererConfiguration {

  private final DocumentAttributes options;
  private final PdfRenderInputs pdfInputs;

  private RendererConfiguration(DocumentAttributes options, PdfRenderInputs pdfInputs) {
    this.options = options;
    this.pdfInputs = pdfInputs;
  }

  public static RendererConfiguration defaults() {
    return new RendererConfiguration(DocumentAttributes.empty(), null);
  }

  public static RendererConfiguration of(DocumentAttributes options) {
    return new RendererConfiguration(options, null);
  }

  public DocumentAttributes options() {
    return options;
  }

  public RendererConfiguration withPdfInputs(PdfRenderInputs pdfInputs) {
    return new RendererConfiguration(this.options, pdfInputs);
  }

  public Optional<PdfRenderInputs> pdfInputs() {
    return Optional.ofNullable(pdfInputs);
  }
}
