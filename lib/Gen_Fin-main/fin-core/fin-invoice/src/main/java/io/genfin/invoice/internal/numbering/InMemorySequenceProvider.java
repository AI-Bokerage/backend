package io.genfin.invoice.internal.numbering;

import io.genfin.invoice.port.numbering.SequenceProvider;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An in-memory, per-{@code sequenceKey} counter — a default for tests/single-node development, not
 * a durable sequence.
 */
public final class InMemorySequenceProvider implements SequenceProvider {

  private final ConcurrentMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

  @Override
  public long next(String sequenceKey) {
    return counters.computeIfAbsent(sequenceKey, key -> new AtomicLong()).incrementAndGet();
  }
}
