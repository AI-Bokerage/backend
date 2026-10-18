package io.genfin.api.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.api.event.OccurredAt;
import io.genfin.api.id.AggregateId;
import io.genfin.api.id.CorrelationId;
import io.genfin.api.id.EventId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AggregateRootTest {

  private static final class TestAggregate extends AggregateRoot<String> {
    TestAggregate(String id) {
      super(id);
    }

    void touch() {
      AggregateId aggregateId = AggregateId.of(id());
      EventMetadata metadata =
          new EventMetadata(
              EventId.generate(),
              new OccurredAt(Instant.EPOCH),
              CorrelationId.generate(),
              aggregateId);
      registerEvent(() -> metadata);
    }
  }

  @Test
  void entitiesWithSameIdAreEqualRegardlessOfState() {
    assertThat(new TestAggregate("A")).isEqualTo(new TestAggregate("A"));
    assertThat(new TestAggregate("A")).isNotEqualTo(new TestAggregate("B"));
  }

  @Test
  void pullEventsDrainsPendingEventsExactlyOnce() {
    TestAggregate aggregate = new TestAggregate("A");
    aggregate.touch();
    aggregate.touch();

    var firstPull = aggregate.pullEvents();
    var secondPull = aggregate.pullEvents();

    assertThat(firstPull)
        .hasSize(2)
        .allSatisfy(event -> assertThat(event).isInstanceOf(DomainEvent.class));
    assertThat(secondPull).isEmpty();
  }
}
