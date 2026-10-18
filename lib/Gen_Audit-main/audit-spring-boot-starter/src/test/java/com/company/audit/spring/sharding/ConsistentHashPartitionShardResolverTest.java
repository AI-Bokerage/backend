package com.company.audit.spring.sharding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ConsistentHashPartitionShardResolverTest {

    @Test
    void singleInstanceTopologyOwnsEveryPartition() {
        ConsistentHashPartitionShardResolver resolver = new ConsistentHashPartitionShardResolver(0, 1);

        for (String partitionKey : samplePartitionKeys()) {
            assertThat(resolver.ownsPartition(partitionKey)).isTrue();
        }
    }

    @Test
    void everyPartitionIsOwnedByExactlyOneOfNConfiguredInstances() {
        int totalInstances = 4;
        List<ConsistentHashPartitionShardResolver> resolvers = IntStream.range(0, totalInstances)
                .mapToObj(index -> new ConsistentHashPartitionShardResolver(index, totalInstances))
                .toList();

        for (String partitionKey : samplePartitionKeys()) {
            long owningInstanceCount =
                    resolvers.stream().filter(resolver -> resolver.ownsPartition(partitionKey)).count();
            assertThat(owningInstanceCount)
                    .as("partition %s must be owned by exactly one instance", partitionKey)
                    .isEqualTo(1);
        }
    }

    @Test
    void assignmentIsDeterministicAndStableAcrossCalls() {
        ConsistentHashPartitionShardResolver resolver = new ConsistentHashPartitionShardResolver(1, 3);
        String partitionKey = "deterministic-test-partition";

        boolean first = resolver.ownsPartition(partitionKey);
        boolean second = resolver.ownsPartition(partitionKey);

        assertThat(first).isEqualTo(second);
        assertThat(resolver.ownerIndex(partitionKey)).isEqualTo(resolver.ownerIndex(partitionKey));
    }

    @Test
    void ownerIndexMatchesWhicheverResolverReportsOwnership() {
        int totalInstances = 5;
        String partitionKey = "owner-index-test-partition";
        ConsistentHashPartitionShardResolver anyResolver = new ConsistentHashPartitionShardResolver(0, totalInstances);
        int ownerIndex = anyResolver.ownerIndex(partitionKey);

        ConsistentHashPartitionShardResolver owningResolver =
                new ConsistentHashPartitionShardResolver(ownerIndex, totalInstances);
        assertThat(owningResolver.ownsPartition(partitionKey)).isTrue();
    }

    @Test
    void rejectsInvalidTopology() {
        assertThatThrownBy(() -> new ConsistentHashPartitionShardResolver(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConsistentHashPartitionShardResolver(-1, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConsistentHashPartitionShardResolver(2, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private List<String> samplePartitionKeys() {
        return IntStream.range(0, 50)
                .mapToObj(i -> "partition-" + i + "-" + UUID.randomUUID())
                .toList();
    }
}
