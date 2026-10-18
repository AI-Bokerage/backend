package io.genfin.document.internal.placeholder;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.placeholder.MissingPlaceholderValue;
import io.genfin.document.api.placeholder.PlaceholderValue;
import io.genfin.document.api.placeholder.ScalarPlaceholderValue;
import io.genfin.document.port.PlaceholderProvider;
import org.junit.jupiter.api.Test;

class DefaultPlaceholderResolverTest {

  private static final class StubProvider implements PlaceholderProvider {
    private final String key;
    private final String value;

    StubProvider(String key, String value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public boolean supports(String candidateKey) {
      return key.equals(candidateKey);
    }

    @Override
    public PlaceholderValue resolve(String candidateKey) {
      return ScalarPlaceholderValue.of(value);
    }
  }

  @Test
  void resolvesThroughFirstSupportingProvider() {
    DefaultPlaceholderRegistry registry = new DefaultPlaceholderRegistry();
    registry.register(new StubProvider("company.name", "Acme"));
    DefaultPlaceholderResolver resolver = new DefaultPlaceholderResolver(registry);

    PlaceholderValue value = resolver.resolve("company.name");

    assertThat(
            value.accept(
                new io.genfin.document.api.placeholder.PlaceholderValueVisitor<String>() {
                  @Override
                  public String visitScalar(ScalarPlaceholderValue v) {
                    return v.value();
                  }

                  @Override
                  public String visitList(
                      io.genfin.document.api.placeholder.ListPlaceholderValue v) {
                    return "list";
                  }

                  @Override
                  public String visitMissing(MissingPlaceholderValue v) {
                    return "missing";
                  }
                }))
        .isEqualTo("Acme");
  }

  @Test
  void returnsMissingWhenNoProviderSupportsKey() {
    DefaultPlaceholderResolver resolver =
        new DefaultPlaceholderResolver(new DefaultPlaceholderRegistry());

    assertThat(resolver.resolve("unknown.key")).isInstanceOf(MissingPlaceholderValue.class);
  }
}
