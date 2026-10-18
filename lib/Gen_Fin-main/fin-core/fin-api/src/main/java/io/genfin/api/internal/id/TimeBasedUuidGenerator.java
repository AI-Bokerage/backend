package io.genfin.api.internal.id;

import io.genfin.api.port.id.IdentifierGenerator;
import io.genfin.api.port.time.ClockProvider;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monotonic, time-ordered identifier generator (UUIDv7-style layout: 48-bit millisecond timestamp
 * followed by random bits), so generated ids sort by creation time.
 */
public final class TimeBasedUuidGenerator implements IdentifierGenerator {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final ClockProvider clockProvider;
  private final AtomicLong sequence = new AtomicLong();

  public TimeBasedUuidGenerator(ClockProvider clockProvider) {
    this.clockProvider = clockProvider;
  }

  @Override
  public String generate() {
    long millis = clockProvider.now().toEpochMilli();
    long mostSigBits = (millis & 0xFFFFFFFFFFFFL) << 16;
    mostSigBits |= 0x7000L | (sequence.incrementAndGet() & 0x0FFFL);

    byte[] randomBytes = new byte[8];
    RANDOM.nextBytes(randomBytes);
    long leastSigBits = 0;
    for (byte b : randomBytes) {
      leastSigBits = (leastSigBits << 8) | (b & 0xFFL);
    }
    leastSigBits = (leastSigBits & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;

    return new UUID(mostSigBits, leastSigBits).toString();
  }
}
