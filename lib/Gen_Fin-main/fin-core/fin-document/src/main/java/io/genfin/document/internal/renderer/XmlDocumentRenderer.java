package io.genfin.document.internal.renderer;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.exception.RenderException;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.model.DocumentElementVisitor;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.TableElement;
import io.genfin.document.api.model.TextBlockElement;
import io.genfin.document.api.result.RenderResult;
import io.genfin.document.port.DocumentRenderer;
import io.genfin.document.port.RendererCapabilities;
import io.genfin.document.port.RendererConfiguration;
import java.nio.charset.StandardCharsets;

public final class XmlDocumentRenderer implements DocumentRenderer {

  private static final RendererId ID = RendererId.of("xml");

  @Override
  public RendererId id() {
    return ID;
  }

  @Override
  public RendererCapabilities capabilities() {
    return () -> "application/xml";
  }

  @Override
  public RenderResult render(ComposedDocument document, RendererConfiguration configuration)
      throws RenderException {
    StringBuilder xml = new StringBuilder();
    xml.append("<document id=\"")
        .append(escapeXmlAttribute(document.metadata().id().value()))
        .append("\">");
    ElementXmlVisitor visitor = new ElementXmlVisitor();
    for (var section : document.sections()) {
      xml.append("<section title=\"").append(escapeXmlAttribute(section.title())).append("\">");
      for (var element : section.elements()) {
        xml.append(element.accept(visitor));
      }
      xml.append("</section>");
    }
    xml.append("</document>");
    return RenderResult.of(
        id(), xml.toString().getBytes(StandardCharsets.UTF_8), capabilities().mimeType());
  }

  private static String escapeXmlAttribute(String value) {
    if (value == null) {
      return "";
    }
    return escapeXmlContent(value).replace("\"", "&quot;");
  }

  private static String escapeXmlContent(String value) {
    if (value == null) {
      return "";
    }
    // Escape & first to avoid double-escaping
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private static final class ElementXmlVisitor implements DocumentElementVisitor<String> {
    @Override
    public String visitKeyValue(KeyValueElement element) {
      return "<field label=\""
          + escapeXmlAttribute(element.label())
          + "\">"
          + escapeXmlContent(element.value())
          + "</field>";
    }

    @Override
    public String visitTable(TableElement element) {
      StringBuilder table = new StringBuilder();
      table.append("<table>");
      table.append("<headers>");
      for (var header : element.headers()) {
        table.append("<header>").append(escapeXmlContent(header)).append("</header>");
      }
      table.append("</headers>");
      for (var row : element.rows()) {
        table.append("<row>");
        for (var cell : row) {
          table.append("<cell>").append(escapeXmlContent(cell)).append("</cell>");
        }
        table.append("</row>");
      }
      table.append("</table>");
      return table.toString();
    }

    @Override
    public String visitTextBlock(TextBlockElement element) {
      return "<text>" + escapeXmlContent(element.text()) + "</text>";
    }
  }
}
