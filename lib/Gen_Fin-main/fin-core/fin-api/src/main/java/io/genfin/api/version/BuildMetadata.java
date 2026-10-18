package io.genfin.api.version;

import io.genfin.api.internal.version.VersionResource;
import io.genfin.api.util.Lazy;

/** Build-time metadata for the framework artifact currently on the classpath. */
public record BuildMetadata(String version, String buildTimestamp) {

  private static final Lazy<BuildMetadata> CURRENT =
      Lazy.of(
          () -> {
            VersionResource resource = new VersionResource();
            return new BuildMetadata(resource.frameworkVersion(), resource.buildTimestamp());
          });

  public static BuildMetadata current() {
    return CURRENT.get();
  }
}
