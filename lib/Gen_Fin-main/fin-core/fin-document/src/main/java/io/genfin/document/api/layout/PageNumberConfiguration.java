package io.genfin.document.api.layout;

import io.genfin.api.validation.Validate;

public final class PageNumberConfiguration {

  private static final PageNumberConfiguration DISABLED = new PageNumberConfiguration(false, "");

  private final boolean enabled;
  private final String format;

  private PageNumberConfiguration(boolean enabled, String format) {
    this.enabled = enabled;
    this.format = format == null ? "" : format;
  }

  public static PageNumberConfiguration disabled() {
    return DISABLED;
  }

  public static PageNumberConfiguration enabled(String format) {
    Validate.notBlank(format, "format must not be blank when page numbers are enabled");
    return new PageNumberConfiguration(true, format);
  }

  public boolean enabled() {
    return enabled;
  }

  public String format() {
    return format;
  }
}
