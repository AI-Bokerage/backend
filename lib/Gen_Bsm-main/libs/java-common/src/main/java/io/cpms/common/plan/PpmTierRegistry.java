package io.cpms.common.plan;

import java.util.Map;
import java.util.Optional;

/**
 * Centralised tier-ranking for PPM plan tiers — the single source of truth
 * both ppm-svc (validates {@code ppm_plans.tier} on create/update) and
 * bsm-svc (decides upgrade-vs-downgrade behavior) must agree on.
 *
 * <p>Tier names are normalised to lower-case before ranking so comparisons are
 * case-insensitive. Unknown tiers return {@link Optional#empty()} — callers
 * must treat an absent rank as "skip the tier guard" rather than failing hard,
 * because PPM plans created before tiers existed may not have one set.
 *
 * <p>Canonical ranking (ascending):
 * <pre>
 *   trial = 0, starter = 1, growth = 2, scale = 3, enterprise = 4
 * </pre>
 */
public final class PpmTierRegistry {

    private static final Map<String, Integer> RANKS = Map.of(
        "trial",      0,
        "starter",    1,
        "growth",     2,
        "scale",      3,
        "enterprise", 4
    );

    private PpmTierRegistry() {}

    /**
     * Returns true if {@code tier} (case-insensitive) is one of the 5 known values.
     */
    public static boolean isValid(String tier) {
        return tier != null && RANKS.containsKey(tier.toLowerCase());
    }

    /**
     * Returns the integer rank for a tier name, or empty if the tier is unknown or null.
     */
    public static Optional<Integer> rankOf(String tier) {
        if (tier == null || tier.isBlank()) return Optional.empty();
        return Optional.ofNullable(RANKS.get(tier.toLowerCase()));
    }

    /**
     * Returns true if {@code targetTier} is strictly higher than {@code currentTier}.
     * Returns false (rather than throwing) when either tier is unknown — this lets
     * the caller decide whether to skip or enforce the guard.
     */
    public static boolean isHigherTier(String currentTier, String targetTier) {
        Optional<Integer> cur = rankOf(currentTier);
        Optional<Integer> tgt = rankOf(targetTier);
        if (cur.isEmpty() || tgt.isEmpty()) return false;
        return tgt.get() > cur.get();
    }

    /**
     * Returns true if {@code targetTier} is strictly lower than {@code currentTier}.
     * Returns false when either tier is unknown.
     */
    public static boolean isLowerTier(String currentTier, String targetTier) {
        Optional<Integer> cur = rankOf(currentTier);
        Optional<Integer> tgt = rankOf(targetTier);
        if (cur.isEmpty() || tgt.isEmpty()) return false;
        return tgt.get() < cur.get();
    }

    /**
     * Returns true if both tiers are known and equal in rank.
     */
    public static boolean isEqualTier(String currentTier, String targetTier) {
        Optional<Integer> cur = rankOf(currentTier);
        Optional<Integer> tgt = rankOf(targetTier);
        if (cur.isEmpty() || tgt.isEmpty()) return false;
        return tgt.get().equals(cur.get());
    }
}
