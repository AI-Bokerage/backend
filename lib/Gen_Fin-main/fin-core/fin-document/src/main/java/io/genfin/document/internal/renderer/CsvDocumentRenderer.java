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

/**
 * Renders {@link TableElement}s as RFC 4180 CSV rows. CSV is inherently tabular, so non-tabular
 * elements ({@link KeyValueElement}, {@link TextBlockElement}) are silently skipped rather than
 * treated as an error — see {@link ElementCsvVisitor#visitKeyValue} and {@link
 * ElementCsvVisitor#visitTextBlock}.
 */
public final class CsvDocumentRenderer implements DocumentRenderer {

  private static final RendererId ID = RendererId.of("csv");

  @Override
  public RendererId id() {
    return ID;
  }

  @Override
  public RendererCapabilities capabilities() {
    return () -> "text/csv";
  }

  @Override
  public RenderResult render(ComposedDocument document, RendererConfiguration configuration)
      throws RenderException {
    StringBuilder csv = new StringBuilder();
    ElementCsvVisitor visitor = new ElementCsvVisitor();
    for (var section : document.sections()) {
      for (var element : section.elements()) {
        String row = element.accept(visitor);
        if (!row.isEmpty()) {
          csv.append(row).append('\n');
        }
      }
    }
    return RenderResult.of(
        id(), csv.toString().getBytes(StandardCharsets.UTF_8), capabilities().mimeType());
  }

  private static String escapeCsvField(String value) {
    if (value == null) {
      return "";
    }
    // RFC 4180: if field contains comma, double-quote, or newline, wrap in quotes
    // and escape any double-quotes by doubling them
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  private static final class ElementCsvVisitor implements DocumentElementVisitor<String> {
    /** Not tabular data — skipped, not an error, per RFC 4180 CSV being inherently tabular. */
    @Override
    public String visitKeyValue(KeyValueElement element) {
      return "";
    }

    @Override
    public String visitTable(TableElement element) {
      StringBuilder csv = new StringBuilder();
      // Escape and join headers
      csv.append(
          String.join(
              ",",
              element.headers().stream()
                  .map(CsvDocumentRenderer::escapeCsvField)
                  .toArray(String[]::new)));
      // Escape and join each row
      for (var row : element.rows()) {
        csv.append('\n');
        csv.append(
            String.join(
                ",", row.stream().map(CsvDocumentRenderer::escapeCsvField).toArray(String[]::new)));
      }
      return csv.toString();
    }

    /** Not tabular data — skipped, not an error, per RFC 4180 CSV being inherently tabular. */
    @Override
    public String visitTextBlock(TextBlockElement element) {
      return "";
    }
  }
}
