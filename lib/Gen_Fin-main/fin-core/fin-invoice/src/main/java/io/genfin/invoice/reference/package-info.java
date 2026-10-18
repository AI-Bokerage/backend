/**
 * A reusable, application-agnostic reference model. The engine never knows what a reference means
 * (project, purchase order, subscription, customer, tenant, ...) — applications register their own
 * {@link ReferenceType}s and attach {@link Reference}s without the engine depending on their
 * meaning.
 */
package io.genfin.invoice.reference;
