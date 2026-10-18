package io.genfin.document.api.brand;

import io.genfin.api.validation.Validate;
import java.util.Arrays;

public final class BrandLogo {

  private final byte[] imageBytes;
  private final String mimeType;

  private BrandLogo(byte[] imageBytes, String mimeType) {
    Validate.notNull(imageBytes, "imageBytes must not be null");
    Validate.notBlank(mimeType, "mimeType must not be blank");
    this.imageBytes = Arrays.copyOf(imageBytes, imageBytes.length);
    this.mimeType = mimeType;
  }

  public static BrandLogo of(byte[] imageBytes, String mimeType) {
    return new BrandLogo(imageBytes, mimeType);
  }

  public byte[] imageBytes() {
    return Arrays.copyOf(imageBytes, imageBytes.length);
  }

  public String mimeType() {
    return mimeType;
  }
}
