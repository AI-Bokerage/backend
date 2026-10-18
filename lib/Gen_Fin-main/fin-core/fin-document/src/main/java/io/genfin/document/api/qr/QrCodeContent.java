package io.genfin.document.api.qr;

import io.genfin.api.validation.Validate;
import java.util.Arrays;

public final class QrCodeContent {

  private final byte[] imageBytes;
  private final String mimeType;

  private QrCodeContent(byte[] imageBytes, String mimeType) {
    Validate.notNull(imageBytes, "imageBytes must not be null");
    Validate.notBlank(mimeType, "mimeType must not be blank");
    this.imageBytes = Arrays.copyOf(imageBytes, imageBytes.length);
    this.mimeType = mimeType;
  }

  public static QrCodeContent of(byte[] imageBytes, String mimeType) {
    return new QrCodeContent(imageBytes, mimeType);
  }

  public byte[] imageBytes() {
    return Arrays.copyOf(imageBytes, imageBytes.length);
  }

  public String mimeType() {
    return mimeType;
  }
}
