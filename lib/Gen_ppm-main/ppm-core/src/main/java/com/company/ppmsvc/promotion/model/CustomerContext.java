package com.company.ppmsvc.promotion.model;

/**
 * Facts about the redeeming customer, supplied by the caller.
 *
 * <p>PPM does not own customer data — it evaluates conditions against facts
 * an external caller (e.g. checkout) provides at quote time.
 */
public record CustomerContext(String customerId, boolean isNewCustomer) {
}
