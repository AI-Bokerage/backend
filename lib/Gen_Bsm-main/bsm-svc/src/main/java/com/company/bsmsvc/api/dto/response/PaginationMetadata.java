package com.company.bsmsvc.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Pagination metadata")
public record PaginationMetadata(
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext
) {
}
