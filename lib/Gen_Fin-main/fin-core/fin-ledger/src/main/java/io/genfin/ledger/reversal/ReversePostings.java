package io.genfin.ledger.reversal;

import io.genfin.ledger.internal.reversal.DefaultReversePosting;
import io.genfin.ledger.port.reversal.ReversePosting;

/** Factory for {@link ReversePosting} instances. */
public final class ReversePostings {

  private static final ReversePosting STANDARD = new DefaultReversePosting();

  private ReversePostings() {}

  public static ReversePosting standard() {
    return STANDARD;
  }
}
