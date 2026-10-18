package io.genfin.autoconfigure.document;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code renderer} mirrors {@code DocumentEngineConfiguration.defaultRenderer}'s own default. */
@ConfigurationProperties(prefix = "genfin.document")
public class DocumentProperties {

  private String renderer = "json";

  public String getRenderer() {
    return renderer;
  }

  public void setRenderer(String renderer) {
    this.renderer = renderer;
  }
}
