package com.company.audit.core.fixture;

import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.exception.SeqConflictException;
import com.company.audit.core.port.ChainRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An in-memory {@link ChainRepository} used only by tests, backed by a map of per-partition
 * record lists.
 */
public final class InMemoryChainRepository implements ChainRepository {

    private final Map<String, List<ChainedRecord>> recordsByPartition = new ConcurrentHashMap<>();

    @Override
    public Optional<ChainedRecord> findTip(String partitionKey) {
        List<ChainedRecord> records = recordsByPartition.get(partitionKey);
        if (records == null || records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(records.size() - 1));
    }

    @Override
    public synchronized void append(ChainedRecord record) throws SeqConflictException {
        List<ChainedRecord> records =
                recordsByPartition.computeIfAbsent(record.event().partitionKey(), k -> new ArrayList<>());
        boolean duplicate = records.stream().anyMatch(r -> r.seq() == record.seq());
        if (duplicate) {
            throw new SeqConflictException(
                    "Duplicate seq " + record.seq() + " for partition "
                            + record.event().partitionKey());
        }
        records.add(record);
    }

    @Override
    public List<ChainedRecord> findAllOrderedBySeq(String partitionKey) {
        List<ChainedRecord> records = recordsByPartition.get(partitionKey);
        if (records == null) {
            return List.of();
        }
        List<ChainedRecord> sorted = new ArrayList<>(records);
        sorted.sort(Comparator.comparingLong(ChainedRecord::seq));
        return sorted;
    }

    /**
     * Test-only helper that replaces the record at the given {@code (partitionKey, seq)} with
     * {@code replacement}, simulating a tamper event that bypasses the appender.
     *
     * @param partitionKey the partition containing the record to corrupt
     * @param seq the sequence number of the record to corrupt
     * @param replacement the record to substitute in its place
     */
    public synchronized void corrupt(String partitionKey, long seq, ChainedRecord replacement) {
        List<ChainedRecord> records = recordsByPartition.get(partitionKey);
        if (records == null) {
            return;
        }
        for (int i = 0; i < records.size(); i++) {
            if (records.get(i).seq() == seq) {
                records.set(i, replacement);
                return;
            }
        }
    }

    /**
     * Test-only helper that removes all stored records, used to isolate test cases from each
     * other.
     */
    public synchronized void clear() {
        recordsByPartition.clear();
    }
}
