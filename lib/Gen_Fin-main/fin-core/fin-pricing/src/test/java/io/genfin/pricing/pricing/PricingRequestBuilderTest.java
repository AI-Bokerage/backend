package io.genfin.pricing.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.spi.ExtensionRegistries;
import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.pricing.id.CatalogId;
import io.genfin.pricing.id.PricingRequestId;
import io.genfin.pricing.lifecycle.PricingRequestStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PricingRequestBuilder} and {@link PricingRequestFactory}. */
class PricingRequestBuilderTest {

  @Test
  void buildsARequestWithGeneratedIdAndDefaultLifecycleWhenUnspecified() {
    PricingRequest.Line line = new PricingRequest.Line(CatalogId.generate(), 2);

    PricingRequest request = PricingRequestBuilder.newRequest().line(line).build();

    assertThat(request.id()).isNotNull();
    assertThat(request.lines()).containsExactly(line);
    assertThat(request.status()).isEqualTo(PricingRequestStatus.CREATED);
  }

  @Test
  void honoursExplicitIdLinesAttributesAndMetadata() {
    PricingRequestId id = PricingRequestId.generate();
    PricingRequest.Line lineOne = new PricingRequest.Line(CatalogId.generate(), 1);
    PricingRequest.Line lineTwo = new PricingRequest.Line(CatalogId.generate(), 3);
    Instant requestedAt = Instant.parse("2026-01-01T00:00:00Z");
    PricingAttributes attributes = PricingAttributes.empty().with("region", "APAC");
    PricingMetadata metadata = PricingMetadata.empty().with("source", "quote");

    PricingRequest request =
        PricingRequestBuilder.newRequest()
            .id(id)
            .lines(List.of(lineOne, lineTwo))
            .requestedAt(requestedAt)
            .attributes(attributes)
            .metadata(metadata)
            .build();

    assertThat(request.id()).isEqualTo(id);
    assertThat(request.lines()).containsExactly(lineOne, lineTwo);
    assertThat(request.requestedAt()).isEqualTo(requestedAt);
    assertThat(request.attributes().find("region")).contains("APAC");
    assertThat(request.metadata().find("source")).contains("quote");
  }

  @Test
  void factoryFallsBackToTheStandardLifecycleWhenNoneIsRegistered() {
    ExtensionRegistry registry = ExtensionRegistries.create();
    PricingRequest.Line line = new PricingRequest.Line(CatalogId.generate(), 1);

    PricingRequest request =
        PricingRequestFactory.create(
            registry, List.of(line), Instant.parse("2026-01-01T00:00:00Z"));

    assertThat(request.status()).isEqualTo(PricingRequestStatus.CREATED);
    assertThat(request.lines()).containsExactly(line);
  }
}
