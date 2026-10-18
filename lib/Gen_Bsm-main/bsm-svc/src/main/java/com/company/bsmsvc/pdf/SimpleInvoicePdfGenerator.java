package com.company.bsmsvc.pdf;

import com.company.bsmsvc.domain.model.InvoiceLineItem;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Slf4j
public class SimpleInvoicePdfGenerator implements InvoicePdfGenerator {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private static final Color HEADER_BG   = new Color(230, 230, 230);
    private static final Color TOTAL_BG    = new Color(245, 245, 245);
    private static final Color GRAND_BG    = new Color(200, 220, 200);

    @Override
    public byte[] generatePdf(PlatformInvoice invoice) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titleFont   = new Font(Font.HELVETICA, 20, Font.BOLD);
            Font sectionFont = new Font(Font.HELVETICA, 11, Font.BOLD);
            Font normalFont  = new Font(Font.HELVETICA, 10, Font.NORMAL);
            Font smallFont   = new Font(Font.HELVETICA,  9, Font.NORMAL);
            Font boldSmall   = new Font(Font.HELVETICA,  9, Font.BOLD);
            Font grandFont   = new Font(Font.HELVETICA, 11, Font.BOLD);

            // ── Title ──────────────────────────────────────────────────────────
            Paragraph title = new Paragraph("INVOICE", titleFont);
            title.setSpacingAfter(4);
            doc.add(title);

            // ── Invoice meta ───────────────────────────────────────────────────
            // Use Instant.now() as fallback when createdAt is null (async PDF generation)
            Instant createdAt = invoice.getCreatedAt() != null ? invoice.getCreatedAt() : Instant.now();

            doc.add(meta("Invoice Number", invoice.getInvoiceNumber(), sectionFont));
            doc.add(meta("Invoice Date  ", DATE_FMT.format(createdAt), normalFont));
            doc.add(meta("Due Date      ", invoice.getDueDate() != null ? invoice.getDueDate().toString() : "N/A", normalFont));
            doc.add(meta("Billing Period",
                    formatDate(invoice.getPeriodStart()) + "  to  " + formatDate(invoice.getPeriodEnd()),
                    normalFont));
            doc.add(meta("Currency      ", invoice.getCurrency() != null ? invoice.getCurrency() : "-", normalFont));
            doc.add(meta("Status        ", String.valueOf(invoice.getStatus()), normalFont));
            if (invoice.getSource() != null) {
                doc.add(meta("Source        ", invoice.getSource().name(), normalFont));
            }
            if (invoice.getPaidAt() != null) {
                doc.add(meta("Paid At       ", DATETIME_FMT.format(invoice.getPaidAt()), normalFont));
            }
            doc.add(spacer());

            // ── Bill To ────────────────────────────────────────────────────────
            doc.add(new Paragraph("Bill To", sectionFont));
            doc.add(new Paragraph("Tenant ID : " + invoice.getTenantId(), normalFont));
            if (invoice.getSubscriptionId() != null) {
                doc.add(new Paragraph("Subscription ID : " + invoice.getSubscriptionId(), normalFont));
            }
            doc.add(spacer());

            // ── Line Items table ───────────────────────────────────────────────
            doc.add(new Paragraph("Line Items", sectionFont));
            PdfPTable table = new PdfPTable(new float[]{5f, 1.2f, 2f, 2f});
            table.setWidthPercentage(100);
            table.setSpacingAfter(6);

            addHeaderCell(table, "Description", boldSmall, HEADER_BG);
            addHeaderCell(table, "Qty",         boldSmall, HEADER_BG);
            addHeaderCell(table, "Unit Price",  boldSmall, HEADER_BG);
            addHeaderCell(table, "Amount",      boldSmall, HEADER_BG);

            List<InvoiceLineItem> lineItems = invoice.getLineItems();
            if (lineItems != null && !lineItems.isEmpty()) {
                for (InvoiceLineItem item : lineItems) {
                    String desc = item.getDescription() != null ? item.getDescription() : "-";
                    table.addCell(cell(desc, smallFont));
                    table.addCell(cellRight(String.valueOf(item.getQuantity()), smallFont));
                    table.addCell(cellRight(formatAmount(item.getUnitAmountMinor()), smallFont));
                    table.addCell(cellRight(formatAmount(item.getAmountMinor()), smallFont));
                }
            } else {
                PdfPCell empty = new PdfPCell(new Phrase("No line items", smallFont));
                empty.setColspan(4);
                empty.setPadding(4);
                table.addCell(empty);
            }
            doc.add(table);

            // ── Totals ─────────────────────────────────────────────────────────
            PdfPTable totals = new PdfPTable(new float[]{3f, 2f});
            totals.setWidthPercentage(38);
            totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totals.setSpacingBefore(4);

            long subtotal = computeSubtotal(lineItems, invoice.getAmountDue());
            addTotalRow(totals, "Subtotal",  formatAmount(subtotal),              normalFont, boldSmall, TOTAL_BG);
            addTotalRow(totals, "Tax",       "0.00",                              normalFont, boldSmall, TOTAL_BG);
            addTotalRow(totals, "Total Due", formatAmount(invoice.getAmountDue()), grandFont,  grandFont, GRAND_BG);
            if (invoice.getAmountPaid() > 0) {
                addTotalRow(totals, "Amount Paid",
                        formatAmount(invoice.getAmountPaid()), normalFont, boldSmall, TOTAL_BG);
                long balance = invoice.getAmountDue() - invoice.getAmountPaid();
                addTotalRow(totals, "Balance Due",
                        formatAmount(Math.max(balance, 0)), grandFont, grandFont, GRAND_BG);
            }
            doc.add(totals);
            doc.add(spacer());

            // ── Footer ─────────────────────────────────────────────────────────
            Font footerFont = new Font(Font.HELVETICA, 8, Font.ITALIC);
            doc.add(new Paragraph("Payment Status: " + invoice.getStatus(), normalFont));
            doc.add(new Paragraph("This document is system-generated and valid without a physical signature.", footerFont));

            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            log.error("[generatePdf] failed to generate PDF for invoiceId={}", invoice.getId(), ex);
            throw new RuntimeException("Failed to generate invoice PDF", ex);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static Paragraph spacer() {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(4);
        return p;
    }

    private static Paragraph meta(String label, String value, Font font) {
        return new Paragraph(label + " : " + value, font);
    }

    private static String formatDate(Instant instant) {
        return instant != null ? DATE_FMT.format(instant) : "N/A";
    }

    private static String formatAmount(long minor) {
        return String.format("%.2f", minor / 100.0);
    }

    private static long computeSubtotal(List<InvoiceLineItem> items, long fallback) {
        if (items == null || items.isEmpty()) return fallback;
        return items.stream().mapToLong(InvoiceLineItem::getAmountMinor).sum();
    }

    private static void addHeaderCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "", font));
        c.setPadding(4);
        return c;
    }

    private static PdfPCell cellRight(String text, Font font) {
        PdfPCell c = cell(text, font);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }

    private static void addTotalRow(PdfPTable table, String label, String value,
                                    Font labelFont, Font valueFont, Color bg) {
        PdfPCell lc = new PdfPCell(new Phrase(label, labelFont));
        lc.setBackgroundColor(bg);
        lc.setPadding(4);
        lc.setBorder(PdfPCell.NO_BORDER);

        PdfPCell vc = new PdfPCell(new Phrase(value, valueFont));
        vc.setBackgroundColor(bg);
        vc.setPadding(4);
        vc.setBorder(PdfPCell.NO_BORDER);
        vc.setHorizontalAlignment(Element.ALIGN_RIGHT);

        table.addCell(lc);
        table.addCell(vc);
    }
}
