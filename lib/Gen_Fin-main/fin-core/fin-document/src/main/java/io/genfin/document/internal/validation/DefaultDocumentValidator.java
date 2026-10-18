package io.genfin.document.internal.validation;

import io.genfin.api.validation.Validate;
import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.exception.RendererNotFoundException;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.validation.ValidationIssue;
import io.genfin.document.api.validation.ValidationResult;
import io.genfin.document.port.DocumentValidator;
import io.genfin.document.port.RendererConfiguration;
import io.genfin.document.port.RendererResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Default {@link DocumentValidator}: checks renderer registration and letterhead format support.
 */
public final class DefaultDocumentValidator implements DocumentValidator {

  private static final Set<String> SUPPORTED_LETTERHEAD_FORMATS =
      Set.of("PDF", "PNG", "JPEG", "BLANK");

  private final RendererResolver resolver;

  public DefaultDocumentValidator(RendererResolver resolver) {
    Validate.notNull(resolver, "resolver must not be null");
    this.resolver = resolver;
  }

  @Override
  public ValidationResult validate(
      ComposedDocument document, RendererConfiguration configuration, RendererId targetRenderer) {
    Validate.notNull(document, "document must not be null");
    Validate.notNull(configuration, "configuration must not be null");
    Validate.notNull(targetRenderer, "targetRenderer must not be null");

    List<ValidationIssue> issues = new ArrayList<>();

    try {
      resolver.resolve(targetRenderer);
    } catch (RendererNotFoundException e) {
      issues.add(
          ValidationIssue.of(
              "RENDERER_NOT_FOUND", "No renderer registered for id: " + targetRenderer));
    }

    configuration
        .pdfInputs()
        .flatMap(inputs -> inputs.letterhead())
        .ifPresent(
            letterhead -> {
              if (!SUPPORTED_LETTERHEAD_FORMATS.contains(letterhead.format().code())) {
                issues.add(
                    ValidationIssue.of(
                        "UNSUPPORTED_LETTERHEAD_FORMAT",
                        "Unsupported letterhead format: " + letterhead.format().code()));
              }
            });

    return issues.isEmpty() ? ValidationResult.valid() : ValidationResult.withIssues(issues);
  }
}
