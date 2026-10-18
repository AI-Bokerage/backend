package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.ProrationPreview;
import com.company.bsmsvc.domain.model.ProrationPreviewFilter;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence/query port for stored proration previews (generated when previewing a plan
 * change before it's applied). Implementations must be thread-safe/stateless.
 */
public interface ProrationPreviewRepositoryPort {

    ProrationPreview save(ProrationPreview preview);

    Optional<ProrationPreview> findById(UUID id);

    PageResult<ProrationPreview> findPreviews(ProrationPreviewFilter filter, int page, int size, String sortBy, String sortDirection);
}
