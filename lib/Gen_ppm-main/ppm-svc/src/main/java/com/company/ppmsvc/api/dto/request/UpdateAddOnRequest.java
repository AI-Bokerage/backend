package com.company.ppmsvc.api.dto.request;

/**
 * PATCH request DTO for updating an existing add-on.
 *
 * <p>All fields are optional. A {@code null} value means "leave unchanged".
 * {@code code} and {@code type} are immutable after creation and must not
 * appear in this DTO (BR-A5, BR-A6).
 */
public record UpdateAddOnRequest(

    String name,

    String description,

    Boolean active
) {}
