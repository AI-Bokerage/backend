package com.example.authsvc.infrastructure.email.template;

import java.time.Year;
import java.util.List;

/**
 * Shared HTML layout for transactional emails.
 *
 * <p>Table-based layout with inline CSS for maximum email-client compatibility
 * (Outlook, Gmail, Apple Mail). All caller-supplied values are HTML-escaped.
 */
public final class EmailHtmlTemplate {

    private static final String PURPLE = "#6C2BD9";

    private EmailHtmlTemplate() {}

    /** A single label/value row rendered inside the bordered info card. */
    public record InfoRow(String label, String value) {}

    /**
     * Renders the full HTML document.
     *
     * @param sectionLabel small right-aligned label next to the logo (e.g. "Security")
     * @param title        large page title
     * @param greeting     optional greeting line (e.g. "Hello John,"); null to omit
     * @param description  body paragraph under the title
     * @param infoRows     optional info-card rows; null or empty to omit the card entirely
     * @param ctaText      optional button label; null to omit the button
     * @param ctaUrl       target URL for the button (required if {@code ctaText} is set)
     * @param subtext      optional small text under the button/description; null to omit
     */
    public static String render(String sectionLabel, String title, String greeting,
                                 String description, List<InfoRow> infoRows,
                                 String ctaText, String ctaUrl, String subtext) {

        String infoCard = (infoRows == null || infoRows.isEmpty()) ? "" : buildInfoCard(infoRows);
        String ctaBlock = (ctaText == null || ctaText.isBlank()) ? "" : buildCta(ctaText, ctaUrl);
        String greetingBlock = (greeting == null || greeting.isBlank()) ? "" : """
                <p style="margin:0 0 16px;font-size:15px;color:#374151;">%s</p>
                """.formatted(esc(greeting));
        String subtextBlock = (subtext == null || subtext.isBlank()) ? "" : """
                <p style="margin:24px 0 0;font-size:13px;line-height:1.6;color:#6b7280;">%s</p>
                """.formatted(esc(subtext));

        int year = Year.now().getValue();

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f5f7fb;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1f2937;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f5f7fb;padding:40px 20px;">
                  <tr>
                    <td align="center">
                      <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0"
                        style="max-width:600px;background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.06);">

                        <!-- Header -->
                        <tr>
                          <td style="padding:32px 40px 20px;border-bottom:1px solid %s33;">
                            <table width="100%%">
                              <tr>
                                <td>
                                  <table cellpadding="0" cellspacing="0">
                                    <tr>
                                      <td style="background:%s;border-radius:50%%;width:32px;height:32px;text-align:center;vertical-align:middle;">
                                        <span style="color:#ffffff;font-size:16px;font-weight:700;">A</span>
                                      </td>
                                      <td style="padding-left:10px;">
                                        <span style="font-size:20px;font-weight:700;color:#111827;">Gen_AUTH</span>
                                      </td>
                                    </tr>
                                  </table>
                                </td>
                                <td align="right">
                                  <span style="font-size:13px;color:#6b7280;">%s</span>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>

                        <!-- Content -->
                        <tr>
                          <td style="padding:32px 40px 40px;">
                            <h1 style="margin:0 0 20px;font-size:26px;line-height:1.3;font-weight:700;color:#111827;">%s</h1>
                            %s
                            <p style="margin:0 0 24px;font-size:15px;line-height:1.7;color:#4b5563;">%s</p>
                            %s
                            %s
                            %s
                          </td>
                        </tr>

                        <!-- Footer -->
                        <tr>
                          <td style="padding:28px 40px;border-top:1px solid #e5e7eb;background:#fafafa;">
                            <table cellpadding="0" cellspacing="0">
                              <tr>
                                <td style="background:%s;border-radius:50%%;width:24px;height:24px;text-align:center;vertical-align:middle;">
                                  <span style="color:#ffffff;font-size:12px;font-weight:700;">A</span>
                                </td>
                                <td style="padding-left:8px;">
                                  <span style="font-size:15px;font-weight:700;color:#111827;">Gen_AUTH</span>
                                </td>
                              </tr>
                            </table>
                            <p style="margin:16px 0 0;font-size:13px;line-height:1.6;color:#6b7280;">
                              This is an automated notification. Please do not reply directly to this email.
                            </p>
                            <p style="margin:8px 0 0;font-size:12px;color:#9ca3af;">
                              &copy; %d Gen_AUTH. All rights reserved.
                            </p>
                          </td>
                        </tr>

                      </table>
                    </td>
                  </tr>
                </table>
                </body>
                </html>
                """.formatted(
                        esc(title), PURPLE, PURPLE, esc(sectionLabel),
                        esc(title), greetingBlock, esc(description),
                        infoCard, ctaBlock, subtextBlock,
                        PURPLE, year);
    }

    private static String buildInfoCard(List<InfoRow> rows) {
        StringBuilder rowsHtml = new StringBuilder();
        for (InfoRow row : rows) {
            rowsHtml.append("""
                    <tr>
                      <td style="padding:10px 0;font-size:14px;color:#6b7280;">%s</td>
                      <td style="padding:10px 0;font-size:14px;font-weight:600;color:#111827;">%s</td>
                    </tr>
                    """.formatted(esc(row.label()), esc(row.value())));
        }
        return """
                <table width="100%%" cellpadding="0" cellspacing="0"
                  style="border:1px solid %s55;border-radius:10px;margin:0 0 24px;">
                  <tr>
                    <td style="padding:20px 24px;">
                      <table width="100%%">
                        %s
                      </table>
                    </td>
                  </tr>
                </table>
                """.formatted(PURPLE, rowsHtml);
    }

    private static String buildCta(String ctaText, String ctaUrl) {
        return """
                <table cellpadding="0" cellspacing="0" style="margin:0 0 8px;">
                  <tr>
                    <td bgcolor="%s" style="border-radius:8px;">
                      <a href="%s"
                         style="display:inline-block;padding:14px 28px;color:#ffffff;text-decoration:none;font-weight:600;font-size:15px;">
                        %s
                      </a>
                    </td>
                  </tr>
                </table>
                """.formatted(PURPLE, esc(ctaUrl == null ? "#" : ctaUrl), esc(ctaText));
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
