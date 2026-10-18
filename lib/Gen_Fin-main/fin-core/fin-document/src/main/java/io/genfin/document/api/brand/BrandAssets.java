package io.genfin.document.api.brand;

import java.util.Optional;

public final class BrandAssets {

  private static final BrandAssets EMPTY = new BrandAssets(null);

  private final BrandLogo logo;

  private BrandAssets(BrandLogo logo) {
    this.logo = logo;
  }

  public static BrandAssets empty() {
    return EMPTY;
  }

  public static BrandAssets of(BrandLogo logo) {
    return new BrandAssets(logo);
  }

  public Optional<BrandLogo> logo() {
    return Optional.ofNullable(logo);
  }
}
