package com.company.ppmsvc.api.dto.request;

/**
 * Request DTO for updating an existing platform capability module.
 *
 * <p>All fields are optional (partial update / PATCH semantics).
 * A {@code null} value means "leave unchanged".
 * If {@code name} is supplied it must not be blank — validated in the service layer.
 *
 * <p>{@code code} is intentionally absent: the module code is immutable
 * after creation and can never be changed via an update.
 */
public record UpdateModuleRequest(

    String  name,
    String  description,
    Boolean active
) {}
