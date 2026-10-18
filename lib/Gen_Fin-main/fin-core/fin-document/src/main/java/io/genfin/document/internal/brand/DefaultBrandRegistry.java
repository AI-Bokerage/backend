package io.genfin.document.internal.brand;

import io.genfin.document.api.brand.BrandProfile;
import io.genfin.document.api.exception.BrandNotFoundException;
import io.genfin.document.api.identity.BrandId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultBrandRegistry {

  private final Map<BrandId, BrandProfile> profiles = new ConcurrentHashMap<>();

  public void register(BrandProfile profile) {
    BrandProfile existing = profiles.putIfAbsent(profile.id(), profile);
    if (existing != null) {
      throw new IllegalStateException("Brand already registered for id: " + profile.id());
    }
  }

  public BrandProfile get(BrandId id) {
    BrandProfile profile = profiles.get(id);
    if (profile == null) {
      throw new BrandNotFoundException(id);
    }
    return profile;
  }
}
