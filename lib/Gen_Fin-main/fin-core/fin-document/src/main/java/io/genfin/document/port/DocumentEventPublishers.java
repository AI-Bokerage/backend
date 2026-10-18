package io.genfin.document.port;

import io.genfin.document.internal.event.DefaultDocumentEventPublisher;

/**
 * Factory for a standard, ready-to-use {@link DocumentEventPublisher} plus a handle to register
 * {@link DocumentEventListener}s against it. This is the only consumer-reachable way to obtain a
 * working {@link DocumentEventPublisher}: the concrete implementation lives in an internal package
 * that the module never exports.
 */
public final class DocumentEventPublishers {

  private DocumentEventPublishers() {}

  public static Bundle standard() {
    DefaultDocumentEventPublisher publisher = new DefaultDocumentEventPublisher();
    return new Bundle() {
      @Override
      public void register(DocumentEventListener listener) {
        publisher.register(listener);
      }

      @Override
      public DocumentEventPublisher publisher() {
        return publisher;
      }
    };
  }

  /**
   * Registration handle and working publisher, wired together against the same backing publisher.
   */
  public interface Bundle {
    void register(DocumentEventListener listener);

    DocumentEventPublisher publisher();
  }
}
