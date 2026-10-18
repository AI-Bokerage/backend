/**
 * Generic, jurisdiction-agnostic tax extension points. Contains zero tax logic — no GST, VAT, or
 * Sales Tax rules or rates. Phase 4 plugs jurisdiction-specific engines into these SPIs without
 * requiring any change to this package or to {@code Money}.
 */
package io.genfin.money.tax;
