package io.genfin.document.api.preview;

import io.genfin.api.validation.Validate;
import io.genfin.document.api.result.RenderResult;
import java.util.Optional;

/**
 * The result of rendering a document for preview purposes: a delegate {@link RenderResult} plus an
 * optional page count.
 */
public final class PreviewResult {

  private final RenderResult renderResult;
  private final Integer pageCount;

  private PreviewResult(RenderResult renderResult, Integer pageCount) {
    Validate.notNull(renderResult, "renderResult must not be null");
    this.renderResult = renderResult;
    this.pageCount = pageCount;
  }

  public static PreviewResult of(RenderResult delegate, Optional<Integer> pageCount) {
    Validate.notNull(pageCount, "pageCount must not be null");
    return new PreviewResult(delegate, pageCount.orElse(null));
  }

  public RenderResult renderResult() {
    return renderResult;
  }

  public Optional<Integer> pageCount() {
    return Optional.ofNullable(pageCount);
  }
}
