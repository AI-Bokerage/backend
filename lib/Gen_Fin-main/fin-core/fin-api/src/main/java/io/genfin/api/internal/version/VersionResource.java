package io.genfin.api.internal.version;

import io.genfin.api.exception.ConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class VersionResource {

  private static final String RESOURCE_PATH = "io/genfin/api/version.properties";

  private final String frameworkVersion;
  private final String buildTimestamp;

  public VersionResource() {
    Properties properties = new Properties();
    try (InputStream stream =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE_PATH)) {
      if (stream == null) {
        throw new ConfigurationException("Missing classpath resource: " + RESOURCE_PATH);
      }
      properties.load(stream);
    } catch (IOException e) {
      throw new ConfigurationException("Failed to load " + RESOURCE_PATH, e);
    }
    this.frameworkVersion = properties.getProperty("framework.version", "unknown");
    this.buildTimestamp = properties.getProperty("build.timestamp", "unknown");
  }

  public String frameworkVersion() {
    return frameworkVersion;
  }

  public String buildTimestamp() {
    return buildTimestamp;
  }
}
