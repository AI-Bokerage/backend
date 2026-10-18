package io.genfin.document.internal.renderer;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.exception.RenderException;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.model.DocumentElementVisitor;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.TableElement;
import io.genfin.document.api.model.TextBlockElement;
import io.genfin.document.api.result.RenderResult;
import io.genfin.document.port.DocumentRenderer;
import io.genfin.document.port.RendererCapabilities;
import io.genfin.document.port.RendererConfiguration;
import java.nio.charset.StandardCharsets;

public final class PlainTextDocumentRenderer implements DocumentRenderer {

  private static final RendererId ID = RendererId.of("text");

  @Override
  public RendererId id() {
    return ID;
  }

  @Override
  public RendererCapabilities capabilities() {
    return () -> "text/plain";
  }

  @Override
  public RenderResult render(ComposedDocument document, RendererConfiguration configuration)
      throws RenderException {
    StringBuilder text = new StringBuilder();
    ElementTextVisitor visitor = new ElementTextVisitor();
    for (DocumentSection section : document.sections()) {
      text.append(section.title()).append('\n');
      for (var element : section.elements()) {
        text.append(element.accept(visitor)).append('\n');
      }
    }
    return RenderResult.of(
        id(), text.toString().getBytes(StandardCharsets.UTF_8), capabilities().mimeType());
  }

  private static final class ElementTextVisitor implements DocumentElementVisitor<String> {
    @Override
    public String visitKeyValue(KeyValueElement element) {
      return element.label() + ": " + element.value();
    }

    @Override
    public String visitTable(TableElement element) {
      StringBuilder table = new StringBuilder();
      table.append(String.join(" | ", element.headers()));
      for (var row : element.rows()) {
        table.append('\n').append(String.join(" | ", row));
      }
      return table.toString();
    }

    @Override
    public String visitTextBlock(TextBlockElement element) {
      return element.text();
    }
  }
}
