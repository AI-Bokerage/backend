package io.genfin.document.internal.renderer.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.document.api.identity.LetterheadId;
import io.genfin.document.api.layout.PageLayout;
import io.genfin.document.api.layout.StandardOrientation;
import io.genfin.document.api.layout.StandardPaperSize;
import io.genfin.document.api.letterhead.Letterhead;
import io.genfin.document.api.letterhead.StandardLetterheadFormat;
import io.genfin.document.internal.renderer.pdf.PdfHeaderFooterPainterTest.Glyph;
import io.genfin.document.internal.renderer.pdf.PdfHeaderFooterPainterTest.GlyphCapture;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class PdfLetterheadOverlayTest {

  private static final int WHITE_RGB = 0xFFFFFF;
  private static final float LETTERHEAD_TEXT_X = 50f;
  private static final float LETTERHEAD_TEXT_Y = 700f;

  private static byte[] tinyLetterheadPdfWithText(String text) throws Exception {
    return letterheadPdf(PDRectangle.A4, text, LETTERHEAD_TEXT_X, LETTERHEAD_TEXT_Y);
  }

  private static byte[] letterheadPdf(PDRectangle pageSize, String text, float x, float y)
      throws Exception {
    try (PDDocument letterheadDoc = new PDDocument()) {
      PDPage page = new PDPage(pageSize);
      letterheadDoc.addPage(page);
      try (PDPageContentStream contentStream = new PDPageContentStream(letterheadDoc, page)) {
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f);
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(text);
        contentStream.endText();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      letterheadDoc.save(out);
      return out.toByteArray();
    }
  }

  private static Letterhead letterhead(StandardLetterheadFormat format, byte[] content) {
    return Letterhead.of(LetterheadId.of("lh-1"), format, content, "application/octet-stream");
  }

  private static PdfDocumentBuilder a4Builder() {
    return new PdfDocumentBuilder(
        PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT).build());
  }

  private static void drawGeneratedContent(PDDocument document, PDPage page) throws Exception {
    try (PDPageContentStream contentStream =
        new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true)) {
      contentStream.beginText();
      contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f);
      contentStream.newLineAtOffset(50, 600);
      contentStream.showText("GENERATED CONTENT");
      contentStream.endText();
    }
  }

  @Test
  void overlayingPdfLetterheadPreservesPageCountAndBothLayersOfText() throws Exception {
    Letterhead letterhead =
        letterhead(
            StandardLetterheadFormat.PDF, tinyLetterheadPdfWithText("LETTERHEAD BACKGROUND"));

    PdfDocumentBuilder documentBuilder = a4Builder();
    PDPage page = documentBuilder.addPage();
    drawGeneratedContent(documentBuilder.document(), page);

    new PdfLetterheadOverlay().overlay(documentBuilder.document(), letterhead);

    assertThat(documentBuilder.document().getNumberOfPages()).isEqualTo(1);
    String text = new PDFTextStripper().getText(documentBuilder.document());
    assertThat(text).contains("LETTERHEAD BACKGROUND");
    assertThat(text).contains("GENERATED CONTENT");

    // The letterhead is a background layer: its glyphs must be drawn before the generated content.
    List<Glyph> glyphs = GlyphCapture.of(documentBuilder.document());
    assertThat(glyphs.get(0).x())
        .isCloseTo(LETTERHEAD_TEXT_X, org.assertj.core.data.Offset.offset(0.01f));
    assertThat(glyphs.get(0).y())
        .isCloseTo(LETTERHEAD_TEXT_Y, org.assertj.core.data.Offset.offset(0.01f));
  }

  @Test
  void blankFormatLetterheadIsANoOp() throws Exception {
    Letterhead blank =
        Letterhead.of(LetterheadId.of("lh-blank"), StandardLetterheadFormat.BLANK, new byte[0], "");
    PdfDocumentBuilder documentBuilder = a4Builder();
    documentBuilder.addPage();

    new PdfLetterheadOverlay().overlay(documentBuilder.document(), blank);

    assertThat(documentBuilder.document().getNumberOfPages()).isEqualTo(1);
    assertThat(documentBuilder.document().getPage(0).hasContents()).isFalse();
  }

  @Test
  void overlayRepeatsOnEveryPage() throws Exception {
    Letterhead letterhead =
        letterhead(StandardLetterheadFormat.PDF, tinyLetterheadPdfWithText("LETTERHEAD"));
    PdfDocumentBuilder documentBuilder = a4Builder();
    documentBuilder.addPage();
    documentBuilder.addPage();
    documentBuilder.addPage();

    new PdfLetterheadOverlay().overlay(documentBuilder.document(), letterhead);

    for (PDPage page : documentBuilder.document().getPages()) {
      assertThat(page.getResources().getXObjectNames()).isNotEmpty();
    }
  }

  // The letterhead PDF's page has a non-zero origin: the letterhead content must still land at the
  // same place on the target page as it appeared on the letterhead's own visible page area.
  @Test
  void overlayNormalisesALetterheadPageWithANonZeroOrigin() throws Exception {
    float originX = 50f;
    float originY = 50f;
    PDRectangle offsetPage =
        new PDRectangle(originX, originY, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
    byte[] content =
        letterheadPdf(
            offsetPage, "LETTERHEAD", originX + LETTERHEAD_TEXT_X, originY + LETTERHEAD_TEXT_Y);

    PdfDocumentBuilder documentBuilder = a4Builder();
    documentBuilder.addPage();

    new PdfLetterheadOverlay()
        .overlay(documentBuilder.document(), letterhead(StandardLetterheadFormat.PDF, content));

    Glyph first = GlyphCapture.of(documentBuilder.document()).get(0);
    assertThat(first.x()).isCloseTo(LETTERHEAD_TEXT_X, org.assertj.core.data.Offset.offset(0.01f));
    assertThat(first.y()).isCloseTo(LETTERHEAD_TEXT_Y, org.assertj.core.data.Offset.offset(0.01f));
  }

  // A LETTER-sized letterhead on an A4 target must be scaled to cover the target page, keeping
  // relative placement, instead of being drawn 1:1 and clipped.
  @Test
  void overlayScalesALetterheadPageSizedDifferentlyFromTheTargetPage() throws Exception {
    byte[] content =
        letterheadPdf(PDRectangle.LETTER, "LETTERHEAD", LETTERHEAD_TEXT_X, LETTERHEAD_TEXT_Y);
    PdfDocumentBuilder documentBuilder = a4Builder();
    PDPage targetPage = documentBuilder.addPage();

    new PdfLetterheadOverlay()
        .overlay(documentBuilder.document(), letterhead(StandardLetterheadFormat.PDF, content));

    float expectedX =
        LETTERHEAD_TEXT_X * targetPage.getMediaBox().getWidth() / PDRectangle.LETTER.getWidth();
    float expectedY =
        LETTERHEAD_TEXT_Y * targetPage.getMediaBox().getHeight() / PDRectangle.LETTER.getHeight();
    Glyph first = GlyphCapture.of(documentBuilder.document()).get(0);
    assertThat(first.x()).isCloseTo(expectedX, org.assertj.core.data.Offset.offset(0.01f));
    assertThat(first.y()).isCloseTo(expectedY, org.assertj.core.data.Offset.offset(0.01f));
  }

  // A target page with a non-zero origin: the letterhead must be translated onto that page's box.
  @Test
  void overlayRespectsANonZeroOriginTargetPage() throws Exception {
    PdfDocumentBuilder documentBuilder = a4Builder();
    PDPage targetPage =
        new PDPage(
            new PDRectangle(20f, 30f, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight()));
    documentBuilder.document().addPage(targetPage);

    new PdfLetterheadOverlay()
        .overlay(
            documentBuilder.document(),
            letterhead(StandardLetterheadFormat.PDF, tinyLetterheadPdfWithText("LETTERHEAD")));

    Glyph first = GlyphCapture.of(documentBuilder.document()).get(0);
    assertThat(first.x())
        .isCloseTo(20f + LETTERHEAD_TEXT_X, org.assertj.core.data.Offset.offset(0.01f));
    assertThat(first.y())
        .isCloseTo(30f + LETTERHEAD_TEXT_Y, org.assertj.core.data.Offset.offset(0.01f));
  }

  // A letterhead page whose CropBox is inset from its MediaBox: only the CropBox is visible, so it
  // is the CropBox that must be mapped onto the target page.
  @Test
  void overlayNormalisesALetterheadPageWithACropBoxInsetFromItsMediaBox() throws Exception {
    float cropInset = 20f;
    PDRectangle cropBox =
        new PDRectangle(
            cropInset,
            cropInset,
            PDRectangle.A4.getWidth() - 2 * cropInset,
            PDRectangle.A4.getHeight() - 2 * cropInset);
    byte[] content;
    try (PDDocument letterheadDoc = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      page.setCropBox(cropBox);
      letterheadDoc.addPage(page);
      try (PDPageContentStream contentStream = new PDPageContentStream(letterheadDoc, page)) {
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f);
        contentStream.newLineAtOffset(cropInset + LETTERHEAD_TEXT_X, cropInset + LETTERHEAD_TEXT_Y);
        contentStream.showText("LETTERHEAD");
        contentStream.endText();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      letterheadDoc.save(out);
      content = out.toByteArray();
    }

    PdfDocumentBuilder documentBuilder = a4Builder();
    PDPage targetPage = documentBuilder.addPage();

    new PdfLetterheadOverlay()
        .overlay(documentBuilder.document(), letterhead(StandardLetterheadFormat.PDF, content));

    float expectedX = LETTERHEAD_TEXT_X * targetPage.getMediaBox().getWidth() / cropBox.getWidth();
    float expectedY =
        LETTERHEAD_TEXT_Y * targetPage.getMediaBox().getHeight() / cropBox.getHeight();
    Glyph first = GlyphCapture.of(documentBuilder.document()).get(0);
    assertThat(first.x()).isCloseTo(expectedX, org.assertj.core.data.Offset.offset(0.01f));
    assertThat(first.y()).isCloseTo(expectedY, org.assertj.core.data.Offset.offset(0.01f));

    // Positioning alone is not visibility: the form's BBox clip could still hide the letterhead.
    // Rasterise the page and assert ink actually lands at the expected spot.
    java.awt.image.BufferedImage rendered =
        new org.apache.pdfbox.rendering.PDFRenderer(documentBuilder.document()).renderImage(0, 1f);
    java.awt.Rectangle ink = inkBounds(rendered);
    assertThat(ink).isNotNull();
    assertThat((float) ink.x).isCloseTo(expectedX, org.assertj.core.data.Offset.offset(3f));
    assertThat((float) (rendered.getHeight() - ink.getMaxY()))
        .isCloseTo(expectedY, org.assertj.core.data.Offset.offset(4f));
  }

  private static java.awt.Rectangle inkBounds(java.awt.image.BufferedImage image) {
    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;
    int maxX = -1;
    int maxY = -1;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if ((image.getRGB(x, y) & WHITE_RGB) != WHITE_RGB) {
          minX = Math.min(minX, x);
          minY = Math.min(minY, y);
          maxX = Math.max(maxX, x);
          maxY = Math.max(maxY, y);
        }
      }
    }
    return maxX < 0 ? null : new java.awt.Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
  }

  @Test
  void overlayingPngLetterheadDrawsAFullPageBackgroundImage() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", out);
    PdfDocumentBuilder documentBuilder = a4Builder();
    PDPage page = documentBuilder.addPage();

    new PdfLetterheadOverlay()
        .overlay(
            documentBuilder.document(),
            letterhead(StandardLetterheadFormat.PNG, out.toByteArray()));

    assertThat(page.getResources().getXObjectNames()).isNotEmpty();
    // The image is scaled onto the page's own MediaBox: "<width> 0 0 <height> <llx> <lly> cm".
    PDRectangle box = page.getMediaBox();
    String stream =
        new String(page.getContents().readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
    java.util.regex.Matcher cm =
        java.util.regex.Pattern.compile("([\\d.]+) 0 0 ([\\d.]+) ([\\d.]+) ([\\d.]+) cm")
            .matcher(stream);
    assertThat(cm.find()).isTrue();
    assertThat(Float.parseFloat(cm.group(1)))
        .isCloseTo(box.getWidth(), org.assertj.core.data.Offset.offset(0.01f));
    assertThat(Float.parseFloat(cm.group(2)))
        .isCloseTo(box.getHeight(), org.assertj.core.data.Offset.offset(0.01f));
    assertThat(Float.parseFloat(cm.group(3))).isZero();
    assertThat(Float.parseFloat(cm.group(4))).isZero();
  }

  @Test
  void svgLetterheadIsExplicitlyUnsupported() {
    PdfDocumentBuilder documentBuilder = a4Builder();
    documentBuilder.addPage();
    Letterhead svg = letterhead(StandardLetterheadFormat.SVG, new byte[] {1, 2, 3});

    assertThatThrownBy(() -> new PdfLetterheadOverlay().overlay(documentBuilder.document(), svg))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("SVG");
  }
}
