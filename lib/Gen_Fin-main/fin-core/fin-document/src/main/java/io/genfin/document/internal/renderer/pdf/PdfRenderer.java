package io.genfin.document.internal.renderer.pdf;

import io.genfin.document.api.brand.BrandLogo;
import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.exception.RenderException;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.layout.PageLayout;
import io.genfin.document.api.layout.StandardOrientation;
import io.genfin.document.api.layout.StandardPaperSize;
import io.genfin.document.api.result.PdfRenderInputs;
import io.genfin.document.api.result.RenderResult;
import io.genfin.document.port.DocumentRenderer;
import io.genfin.document.port.RendererCapabilities;
import io.genfin.document.port.RendererConfiguration;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Orchestrates the PDF rendering pipeline: paints content, then header/footer/page-numbers (which
 * need a two-pass render to know the total page count), then logo/watermark/QR/letterhead
 * decorations.
 */
public final class PdfRenderer implements DocumentRenderer {

  private static final RendererId ID = RendererId.of("pdf");
  private static final RendererCapabilities CAPABILITIES = () -> "application/pdf";

  @Override
  public RendererId id() {
    return ID;
  }

  @Override
  public RendererCapabilities capabilities() {
    return CAPABILITIES;
  }

  @Override
  public RenderResult render(ComposedDocument document, RendererConfiguration configuration)
      throws RenderException {
    PdfRenderInputs inputs =
        configuration
            .pdfInputs()
            .orElseGet(
                () ->
                    PdfRenderInputs.builder(
                            PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT)
                                .build())
                        .build());

    try {
      return RenderResult.of(id(), renderPdf(document, inputs), capabilities().mimeType());
    } catch (IOException e) {
      throw new RenderException(id(), "Failed to render PDF", e);
    }
  }

  private byte[] renderPdf(ComposedDocument document, PdfRenderInputs inputs) throws IOException {
    int totalPages = countTotalPages(document, inputs);
    try (PDDocument pdDocument = paintContent(document, inputs)) {
      paintHeaderFooter(pdDocument, inputs, totalPages);
      applyDecorations(pdDocument, inputs);

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      pdDocument.save(out);
      return out.toByteArray();
    }
  }

  private int countTotalPages(ComposedDocument document, PdfRenderInputs inputs)
      throws IOException {
    try (PDDocument countingDocument = paintContent(document, inputs)) {
      return countingDocument.getNumberOfPages();
    }
  }

  private PDDocument paintContent(ComposedDocument document, PdfRenderInputs inputs)
      throws IOException {
    PdfDocumentBuilder documentBuilder = new PdfDocumentBuilder(inputs.layout());
    new PdfContentPainter(documentBuilder).paint(document);
    return documentBuilder.document();
  }

  private void paintHeaderFooter(PDDocument pdDocument, PdfRenderInputs inputs, int totalPages)
      throws IOException {
    PdfHeaderFooterPainter headerFooterPainter =
        new PdfHeaderFooterPainter(new PdfDocumentBuilder(inputs.layout()));
    int pageNumber = 1;
    for (PDPage page : pdDocument.getPages()) {
      headerFooterPainter.paint(
          page,
          inputs.layout().header(),
          inputs.layout().footer(),
          inputs.layout().pageNumbers(),
          pageNumber,
          totalPages);
      pageNumber++;
    }
  }

  private void applyDecorations(PDDocument pdDocument, PdfRenderInputs inputs) throws IOException {
    // A fresh PdfDocumentBuilder here is only used for its margin-box geometry (contentTop() etc);
    // PdfImagePlacer/PdfWatermarkStamper draw onto whatever PDPage is passed to them explicitly, so
    // this throwaway builder's own PDDocument is never touched.
    PdfDocumentBuilder documentBuilder = new PdfDocumentBuilder(inputs.layout());
    PdfImagePlacer imagePlacer = new PdfImagePlacer(documentBuilder);
    PdfWatermarkStamper watermarkStamper = new PdfWatermarkStamper(documentBuilder);

    if (inputs.letterhead().isPresent()) {
      new PdfLetterheadOverlay().overlay(pdDocument, inputs.letterhead().get());
    }

    boolean firstPage = true;
    for (PDPage page : pdDocument.getPages()) {
      if (firstPage) {
        drawLogo(imagePlacer, page, inputs);
      }
      if (inputs.watermark().isPresent()) {
        watermarkStamper.stamp(page, inputs.watermark().get());
      }
      for (var qrEntry : inputs.qrCodes().entrySet()) {
        imagePlacer.drawBottomRight(page, qrEntry.getValue().imageBytes());
      }
      firstPage = false;
    }
  }

  private void drawLogo(PdfImagePlacer imagePlacer, PDPage page, PdfRenderInputs inputs) {
    inputs
        .brandProfile()
        .flatMap(brandProfile -> brandProfile.assets().logo())
        .ifPresent(logo -> drawLogo(imagePlacer, page, logo));
  }

  private void drawLogo(PdfImagePlacer imagePlacer, PDPage page, BrandLogo logo) {
    try {
      imagePlacer.drawTopLeft(page, logo.imageBytes());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
