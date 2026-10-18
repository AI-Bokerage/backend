package io.genfin.document.port;

/** Describes what a {@link DocumentRenderer} produces, e.g. its output MIME type. */
public interface RendererCapabilities {
  String mimeType();
}
