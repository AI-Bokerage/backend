package io.genfin.document.internal.renderer.pdf;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.model.DocumentElementVisitor;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.TableElement;
import io.genfin.document.api.model.TextBlockElement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Paints a {@link ComposedDocument}'s text onto PDF pages top-down, wrapping lines to the content
 * width and starting a new page whenever the vertical cursor runs out of room.
 */
final class PdfContentPainter {

  private static final float BODY_FONT_SIZE = 11f;
  private static final float TITLE_FONT_SIZE = 13f;
  private static final float LINE_HEIGHT = 14f;
  private static final String CELL_SEPARATOR = "  |  ";
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private final PdfDocumentBuilder documentBuilder;
  private final PDFont bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
  private final PDFont titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

  private PDPage currentPage;
  private float cursorY;

  PdfContentPainter(PdfDocumentBuilder documentBuilder) {
    this.documentBuilder = documentBuilder;
  }

  void paint(ComposedDocument document) throws IOException {
    startNewPage();
    LineVisitor visitor = new LineVisitor();
    for (DocumentSection section : document.sections()) {
      drawWrapped(section.title(), titleFont, TITLE_FONT_SIZE);
      for (var element : section.elements()) {
        for (Line line : element.accept(visitor)) {
          drawWrapped(line.text(), line.bold() ? titleFont : bodyFont, BODY_FONT_SIZE);
        }
      }
    }
  }

  private void startNewPage() {
    currentPage = documentBuilder.addPage();
    // Advance one line height before the first draw: the baseline sits at cursorY, so drawing at
    // contentTop() itself would put the glyph tops above the page (invisible with zero margins).
    cursorY = documentBuilder.contentTop() - LINE_HEIGHT;
  }

  private void drawWrapped(String text, PDFont font, float fontSize) throws IOException {
    if (text == null || text.isBlank()) {
      drawLine(" ", font, fontSize);
      return;
    }
    // Collapse whitespace runs (including \n and \t, which WinAnsi cannot encode) to single spaces
    // BEFORE sanitizing, otherwise every line break in a multi-line text block becomes a "?".
    String normalized = WHITESPACE.matcher(text).replaceAll(" ");
    for (String line : wrap(sanitize(normalized, font), font, fontSize)) {
      drawLine(line, font, fontSize);
    }
  }

  /**
   * Replaces every character the font cannot encode with {@code ?}. The Standard-14 fonts only
   * support WinAnsiEncoding, and PDFBox throws {@link IllegalArgumentException} on both measuring
   * and drawing an unsupported character — without this, one CJK glyph or a stray {@code ₹} fails
   * the whole document render.
   *
   * <p>ponytail: degrades unsupported glyphs to {@code ?}; embed a Unicode TrueType font when real
   * non-Latin script support is required.
   */
  private static String sanitize(String text, PDFont font) {
    if (isEncodable(text, font)) {
      return text;
    }
    StringBuilder sanitized = new StringBuilder(text.length());
    text.codePoints()
        .forEach(
            codePoint -> {
              String character = Character.toString(codePoint);
              sanitized.append(isEncodable(character, font) ? character : "?");
            });
    return sanitized.toString();
  }

  private static boolean isEncodable(String text, PDFont font) {
    try {
      font.getStringWidth(text);
      return true;
    } catch (IOException | IllegalArgumentException e) {
      return false;
    }
  }

  /** Greedy word wrap; a single word too wide for the content width is broken character-wise. */
  private List<String> wrap(String text, PDFont font, float fontSize) throws IOException {
    float maxWidth = documentBuilder.contentWidth();
    List<String> lines = new ArrayList<>();
    StringBuilder line = new StringBuilder();
    for (String word : WHITESPACE.splitAsStream(text.trim()).toList()) {
      String candidate = line.isEmpty() ? word : line + " " + word;
      if (widthOf(candidate, font, fontSize) <= maxWidth) {
        line.setLength(0);
        line.append(candidate);
        continue;
      }
      if (!line.isEmpty()) {
        lines.add(line.toString());
        line.setLength(0);
      }
      String remainder = word;
      while (widthOf(remainder, font, fontSize) > maxWidth) {
        int fitting = countFittingCharacters(remainder, font, fontSize, maxWidth);
        lines.add(remainder.substring(0, fitting));
        remainder = remainder.substring(fitting);
      }
      line.append(remainder);
    }
    if (!line.isEmpty()) {
      lines.add(line.toString());
    }
    return lines;
  }

  private int countFittingCharacters(String word, PDFont font, float fontSize, float maxWidth)
      throws IOException {
    int fitting = 1;
    while (fitting < word.length()
        && widthOf(word.substring(0, fitting + 1), font, fontSize) <= maxWidth) {
      fitting++;
    }
    return fitting;
  }

  private float widthOf(String text, PDFont font, float fontSize) throws IOException {
    return font.getStringWidth(text) / 1000f * fontSize;
  }

  private void drawLine(String text, PDFont font, float fontSize) throws IOException {
    if (cursorY - LINE_HEIGHT < documentBuilder.contentBottom()) {
      startNewPage();
    }
    try (PDPageContentStream contentStream =
        new PDPageContentStream(
            documentBuilder.document(), currentPage, PDPageContentStream.AppendMode.APPEND, true)) {
      contentStream.beginText();
      contentStream.setFont(font, fontSize);
      contentStream.newLineAtOffset(documentBuilder.contentLeft(), cursorY);
      contentStream.showText(text);
      contentStream.endText();
    }
    cursorY -= LINE_HEIGHT;
  }

  /** A single logical line of text, before wrapping, plus whether it is drawn bold. */
  private record Line(String text, boolean bold) {}

  private static final class LineVisitor implements DocumentElementVisitor<List<Line>> {
    @Override
    public List<Line> visitKeyValue(KeyValueElement element) {
      return List.of(new Line(element.label() + ": " + element.value(), false));
    }

    @Override
    public List<Line> visitTable(TableElement element) {
      List<Line> lines = new ArrayList<>();
      lines.add(new Line(String.join(CELL_SEPARATOR, element.headers()), true));
      for (var row : element.rows()) {
        lines.add(new Line(String.join(CELL_SEPARATOR, row), false));
      }
      return lines;
    }

    @Override
    public List<Line> visitTextBlock(TextBlockElement element) {
      return List.of(new Line(element.text(), false));
    }
  }
}
