package io.genfin.document.port;

import io.genfin.document.api.brand.BrandProfile;
import io.genfin.document.api.identity.BrandId;

public interface BrandResolver {
  BrandProfile resolve(BrandId id);
}
