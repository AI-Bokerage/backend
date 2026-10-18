package io.genfin.document.internal.renderer.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.layout.LayoutFooter;
import io.genfin.document.api.layout.LayoutHeader;
import io.genfin.document.api.layout.Margins;
import io.genfin.document.api.layout.PageLayout;
import io.genfin.document.api.layout.PageNumberConfiguration;
import io.genfin.document.api.layout.StandardOrientation;
import io.genfin.document.api.layout.StandardPaperSize;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;
import org.junit.jupiter.api.Test;

class PdfHeaderFooterPainterTest {

  @Test
  void paintsHeaderAndFooterText() throws Exception {
    PageLayout layout =
        PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT).build();
    PdfDocumentBuilder documentBuilder = new PdfDocumentBuilder(layout);
    PDPage page = documentBuilder.addPage();
    PdfHeaderFooterPainter painter = new PdfHeaderFooterPainter(documentBuilder);

    painter.paint(
        page,
        LayoutHeader.of("Header text"),
        LayoutFooter.of("Footer text"),
        PageNumberConfiguration.disabled(),
        1,
        1);

    String text = new PDFTextStripper().getText(documentBuilder.document());
    assertThat(text).contains("Header text");
    assertThat(text).contains("Footer text");
  }

  @Test
  void substitutesPageNumberTokensWhenEnabled() throws Exception {
    PageLayout layout =
        PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT).build();
    PdfDocumentBuilder documentBuilder = new PdfDocumentBuilder(layout);
    PDPage page = documentBuilder.addPage();
    PdfHeaderFooterPainter painter = new PdfHeaderFooterPainter(documentBuilder);

    painter.paint(
        page,
        LayoutHeader.of(""),
        LayoutFooter.of(""),
        PageNumberConfiguration.enabled("Page {n} of {total}"),
        2,
        5);

    String text = new PDFTextStripper().getText(documentBuilder.document());
    assertThat(text).contains("Page 2 of 5");
  }

  // Text extraction succeeds even for glyphs drawn off the page, so visibility is asserted against
  // the real glyph positions replayed from the page's content stream.
  @Test
  void headerFooterAndPageNumberGlyphsAreInsideThePageWithZeroMargins() throws Exception {
    assertAllGlyphsVisible(Margins.none());
  }

  @Test
  void headerFooterAndPageNumberGlyphsAreInsideThePageWithLargeMargins() throws Exception {
    assertAllGlyphsVisible(Margins.uniform(72));
  }

  @Test
  void pageNumberIsRightAlignedWithinTheContentWidth() throws Exception {
    PageLayout layout =
        PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT)
            .margins(Margins.uniform(36))
            .build();
    PdfDocumentBuilder documentBuilder = new PdfDocumentBuilder(layout);
    PDPage page = documentBuilder.addPage();

    new PdfHeaderFooterPainter(documentBuilder)
        .paint(
            page,
            LayoutHeader.of(""),
            LayoutFooter.of("Footer text"),
            PageNumberConfiguration.enabled("Page {n} of {total}"),
            2,
            5);

    List<Glyph> glyphs = GlyphCapture.of(documentBuilder.document());
    float footerLeft = glyphs.stream().map(Glyph::x).min(Float::compare).orElseThrow();
    float pageNumberRight = glyphs.stream().map(Glyph::x).max(Float::compare).orElseThrow();
    float contentRight = documentBuilder.contentLeft() + documentBuilder.contentWidth();

    assertThat(footerLeft).isEqualTo(documentBuilder.contentLeft());
    assertThat(pageNumberRight).isLessThan(contentRight);
    // Footer and page number share one baseline.
    assertThat(glyphs.stream().map(Glyph::y).distinct()).hasSize(1);
  }

  private static void assertAllGlyphsVisible(Margins margins) throws Exception {
    PageLayout layout =
        PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT)
            .margins(margins)
            .build();
    PdfDocumentBuilder documentBuilder = new PdfDocumentBuilder(layout);
    PDPage page = documentBuilder.addPage();

    new PdfHeaderFooterPainter(documentBuilder)
        .paint(
            page,
            LayoutHeader.of("Header text"),
            LayoutFooter.of("Footer text"),
            PageNumberConfiguration.enabled("Page {n} of {total}"),
            2,
            5);

    List<Glyph> glyphs = GlyphCapture.of(documentBuilder.document());
    PDRectangle box = page.getMediaBox();
    assertThat(glyphs).isNotEmpty();
    assertThat(glyphs)
        .allSatisfy(
            glyph -> {
              assertThat(glyph.x()).isBetween(box.getLowerLeftX(), box.getUpperRightX());
              assertThat(glyph.y()).isBetween(box.getLowerLeftY(), box.getUpperRightY());
            });
  }

  record Glyph(float x, float y) {}

  /**
   * Replays a page's real content stream and records the user-space position of every glyph
   * actually drawn, so assertions run against drawn geometry rather than recomputed expectations.
   */
  static final class GlyphCapture extends PDFTextStripper {

    private final List<Glyph> glyphs = new ArrayList<>();

    static List<Glyph> of(PDDocument document) throws IOException {
      GlyphCapture capture = new GlyphCapture();
      capture.getText(document);
      return capture.glyphs;
    }

    @Override
    protected void showGlyph(Matrix textRenderingMatrix, PDFont font, int code, Vector displacement)
        throws IOException {
      super.showGlyph(textRenderingMatrix, font, code, displacement);
      glyphs.add(
          new Glyph(textRenderingMatrix.getTranslateX(), textRenderingMatrix.getTranslateY()));
    }
  }
}
