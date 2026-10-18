package com.company.bsmsvc.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Generic paginated response payload")
public record PaginatedPayload<T>(
    List<T> data,
    PaginationMetadata pagination
) {
}
