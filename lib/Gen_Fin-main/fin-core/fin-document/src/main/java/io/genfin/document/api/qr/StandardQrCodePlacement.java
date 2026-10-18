package io.genfin.document.api.qr;

import io.genfin.api.validation.Validate;

public record StandardQrCodePlacement(String code) implements QrCodePlacement {

  public StandardQrCodePlacement {
    Validate.notBlank(code, "QrCodePlacement code must not be blank");
  }

  public static StandardQrCodePlacement of(String code) {
    return new StandardQrCodePlacement(code);
  }

  public static final StandardQrCodePlacement BOTTOM_RIGHT = of("BOTTOM_RIGHT");
}
