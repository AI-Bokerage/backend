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
import java.util.List;

/**
 * Renders a {@link ComposedDocument} as Markdown, with GitHub-flavored tables for table elements.
 */
public final class MarkdownDocumentRenderer implements DocumentRenderer {

  private static final RendererId ID = RendererId.of("markdown");

  @Override
  public RendererId id() {
    return ID;
  }

  @Override
  public RendererCapabilities capabilities() {
    return () -> "text/markdown";
  }

  @Override
  public RenderResult render(ComposedDocument document, RendererConfiguration configuration)
      throws RenderException {
    StringBuilder markdown = new StringBuilder();
    ElementMarkdownVisitor visitor = new ElementMarkdownVisitor();
    for (var section : document.sections()) {
      markdown.append("## ").append(section.title()).append('\n');
      for (var element : section.elements()) {
        markdown.append(element.accept(visitor)).append('\n');
      }
    }
    return RenderResult.of(
        id(), markdown.toString().getBytes(StandardCharsets.UTF_8), capabilities().mimeType());
  }

  private static String escapePipe(String value) {
    return value == null ? "" : value.replace("|", "\\|");
  }

  private static String escapeBold(String value) {
    return value == null ? "" : value.replace("*", "\\*");
  }

  private static final class ElementMarkdownVisitor implements DocumentElementVisitor<String> {
    @Override
    public String visitKeyValue(KeyValueElement element) {
      return "- **" + escapeBold(element.label()) + "**: " + element.value();
    }

    @Override
    public String visitTable(TableElement element) {
      List<String> headers =
          element.headers().stream().map(MarkdownDocumentRenderer::escapePipe).toList();
      StringBuilder table = new StringBuilder();
      table.append("| ").append(String.join(" | ", headers)).append(" |");
      table.append('\n');
      table.append("| --- ".repeat(headers.size()));
      table.append("|");
      for (var row : element.rows()) {
        List<String> cells = row.stream().map(MarkdownDocumentRenderer::escapePipe).toList();
        table.append('\n').append("| ").append(String.join(" | ", cells)).append(" |");
      }
      return table.toString();
    }

    @Override
    public String visitTextBlock(TextBlockElement element) {
      return element.text();
    }
  }
}
