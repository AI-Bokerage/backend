package io.genfin.tax.api.config;

/**
 * The default export treatment applied when a seller's own registration doesn't already imply one
 * (an {@code LUT_REGISTERED} seller always gets {@code EXPORT_WITH_LUT} regardless of this setting;
 * an {@code EXPORTER}-flagged seller without an LUT always gets {@code EXPORT_WITH_IGST}). A plain,
 * closed choice — not an open value type — since it's a binary configuration switch, not an
 * extensible domain concept.
 */
public enum ExportTreatment {
  EXPORT_WITH_LUT,
  EXPORT_WITH_IGST
}
