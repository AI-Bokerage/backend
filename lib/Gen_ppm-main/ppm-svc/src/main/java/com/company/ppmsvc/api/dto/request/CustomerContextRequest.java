package com.company.ppmsvc.api.dto.request;

/**
 * Nested request payload carrying customer facts for {@code POST
 * /api/v1/ppm/quotes}. Both fields nullable — when the whole object is
 * omitted, the pricing engine falls back to the Phase-0 {@code quote(...)}
 * path (conditions skipped).
 */
public record CustomerContextRequest(

    String  customerId,

    Boolean isNewCustomer
) {}
