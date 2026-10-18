/**
 * Arbitrary structured invoice metadata — instead of hundreds of optional fields, applications
 * attach {@link TypedValue}-backed {@link CustomField}s via {@link Metadata}, simple string tags
 * via {@link Attributes}, or namespaced plugin data via {@link ExtensionProperties}.
 */
package io.genfin.invoice.metadata;
