package io.genfin.document.api.exception;

import io.genfin.api.exception.GenFinException;
import io.genfin.document.api.identity.BrandId;
import java.util.Map;

public final class BrandNotFoundException extends GenFinException {

  public BrandNotFoundException(BrandId brandId) {
    super(
        DocumentErrorCode.BRAND_NOT_FOUND,
        "No brand registered for id: " + brandId,
        null,
        Map.of("brandId", brandId));
  }
}
