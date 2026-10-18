/**
 * The Gen-Fin Invoice Engine: a reusable financial-invoice domain model. Contains no persistence,
 * REST, Spring, PDF, payment, or application-specific concepts (Project, Tenant, Customer, ...).
 * Applications adapt their own domain to this engine via {@link
 * io.genfin.invoice.reference.Reference} and {@link io.genfin.invoice.metadata.Metadata} rather
 * than this engine adapting to them.
 */
package io.genfin.invoice;
