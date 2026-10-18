package io.genfin.api.version;

import io.genfin.api.internal.version.VersionResource;
import io.genfin.api.util.Lazy;

/** The version of the Gen-Fin framework currently on the classpath. */
public record FrameworkVersion(String value) {

  private static final Lazy<FrameworkVersion> CURRENT =
      Lazy.of(() -> new FrameworkVersion(new VersionResource().frameworkVersion()));

  public static FrameworkVersion current() {
    return CURRENT.get();
  }
}
