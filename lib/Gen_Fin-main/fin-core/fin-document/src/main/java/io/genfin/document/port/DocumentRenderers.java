package io.genfin.document.port;

import io.genfin.document.internal.DefaultRendererRegistry;
import io.genfin.document.internal.DefaultRendererResolver;
import io.genfin.document.internal.renderer.CsvDocumentRenderer;
import io.genfin.document.internal.renderer.JsonDocumentRenderer;
import io.genfin.document.internal.renderer.MarkdownDocumentRenderer;
import io.genfin.document.internal.renderer.PlainTextDocumentRenderer;
import io.genfin.document.internal.renderer.XmlDocumentRenderer;
import io.genfin.document.internal.renderer.pdf.PdfRenderer;

/** Factory for the standard {@link RendererResolver}, pre-populated with the built-in renderers. */
public final class DocumentRenderers {

  private DocumentRenderers() {}

  public static RendererResolver standard() {
    DefaultRendererRegistry registry = new DefaultRendererRegistry();
    registry.register(new JsonDocumentRenderer());
    registry.register(new PlainTextDocumentRenderer());
    registry.register(new MarkdownDocumentRenderer());
    registry.register(new CsvDocumentRenderer());
    registry.register(new XmlDocumentRenderer());
    registry.register(new PdfRenderer());
    return new DefaultRendererResolver(registry);
  }
}
