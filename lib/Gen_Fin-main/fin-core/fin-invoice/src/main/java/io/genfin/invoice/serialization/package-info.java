/**
 * Serialization contracts built only on {@code fin-api}'s {@code Codec} — no Jackson.
 *
 * <p>ponytail: only {@link ReferenceCodec} is provided. This phase is explicitly persistence-free,
 * and {@code Invoice} is a live aggregate (its lifecycle {@code StateMachine} isn't meaningfully
 * serializable, only replayable via events) — a full aggregate codec belongs with whichever future
 * module owns persistence/snapshots, which can compose {@code ReferenceCodec} and the {@code
 * Money}/ {@code Currency} codecs from {@code fin-money} rather than reinventing them.
 */
package io.genfin.invoice.serialization;
