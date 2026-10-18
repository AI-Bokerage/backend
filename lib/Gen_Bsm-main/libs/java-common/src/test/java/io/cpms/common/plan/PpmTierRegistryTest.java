package io.cpms.common.plan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PpmTierRegistryTest {

    @Nested
    class IsValid {
        @Test
        void knownTiers_areValid() {
            assertThat(PpmTierRegistry.isValid("trial")).isTrue();
            assertThat(PpmTierRegistry.isValid("starter")).isTrue();
            assertThat(PpmTierRegistry.isValid("growth")).isTrue();
            assertThat(PpmTierRegistry.isValid("scale")).isTrue();
            assertThat(PpmTierRegistry.isValid("enterprise")).isTrue();
        }

        @Test
        void isCaseInsensitive() {
            assertThat(PpmTierRegistry.isValid("GROWTH")).isTrue();
            assertThat(PpmTierRegistry.isValid("Enterprise")).isTrue();
        }

        @Test
        void unknownOrNull_isInvalid() {
            assertThat(PpmTierRegistry.isValid("premium")).isFalse();
            assertThat(PpmTierRegistry.isValid(null)).isFalse();
        }
    }

    @Nested
    class RankOf {
        @Test
        void knownTiers_returnCorrectRanks() {
            assertThat(PpmTierRegistry.rankOf("trial")).hasValue(0);
            assertThat(PpmTierRegistry.rankOf("starter")).hasValue(1);
            assertThat(PpmTierRegistry.rankOf("growth")).hasValue(2);
            assertThat(PpmTierRegistry.rankOf("scale")).hasValue(3);
            assertThat(PpmTierRegistry.rankOf("enterprise")).hasValue(4);
        }

        @Test
        void tierNamesAreCaseInsensitive() {
            assertThat(PpmTierRegistry.rankOf("GROWTH")).hasValue(2);
            assertThat(PpmTierRegistry.rankOf("Growth")).hasValue(2);
            assertThat(PpmTierRegistry.rankOf("ENTERPRISE")).hasValue(4);
        }

        @Test
        void unknownTier_returnsEmpty() {
            assertThat(PpmTierRegistry.rankOf("premium")).isEmpty();
            assertThat(PpmTierRegistry.rankOf("basic")).isEmpty();
        }

        @Test
        void nullOrBlank_returnsEmpty() {
            assertThat(PpmTierRegistry.rankOf(null)).isEmpty();
            assertThat(PpmTierRegistry.rankOf("")).isEmpty();
            assertThat(PpmTierRegistry.rankOf("   ")).isEmpty();
        }
    }

    @Nested
    class IsHigherTier {
        @Test
        void higherTier_growth_to_scale() {
            assertThat(PpmTierRegistry.isHigherTier("growth", "scale")).isTrue();
        }

        @Test
        void higherTier_trial_to_enterprise() {
            assertThat(PpmTierRegistry.isHigherTier("trial", "enterprise")).isTrue();
        }

        @Test
        void lowerTier_scale_to_growth() {
            assertThat(PpmTierRegistry.isHigherTier("scale", "growth")).isFalse();
        }

        @Test
        void equalTier_false() {
            assertThat(PpmTierRegistry.isHigherTier("growth", "growth")).isFalse();
        }

        @Test
        void unknownCurrentTier_false() {
            assertThat(PpmTierRegistry.isHigherTier("unknown", "scale")).isFalse();
        }

        @Test
        void unknownTargetTier_false() {
            assertThat(PpmTierRegistry.isHigherTier("growth", "unknown")).isFalse();
        }
    }

    @Nested
    class IsLowerTier {
        @Test
        void lowerTier_scale_to_growth() {
            assertThat(PpmTierRegistry.isLowerTier("scale", "growth")).isTrue();
        }

        @Test
        void lowerTier_enterprise_to_trial() {
            assertThat(PpmTierRegistry.isLowerTier("enterprise", "trial")).isTrue();
        }

        @Test
        void higherTier_growth_to_scale() {
            assertThat(PpmTierRegistry.isLowerTier("growth", "scale")).isFalse();
        }

        @Test
        void equalTier_false() {
            assertThat(PpmTierRegistry.isLowerTier("starter", "starter")).isFalse();
        }

        @Test
        void unknownTier_false() {
            assertThat(PpmTierRegistry.isLowerTier("unknown", "starter")).isFalse();
        }
    }

    @Nested
    class IsEqualTier {
        @Test
        void sameTier_true() {
            assertThat(PpmTierRegistry.isEqualTier("growth", "growth")).isTrue();
        }

        @Test
        void differentTiers_false() {
            assertThat(PpmTierRegistry.isEqualTier("growth", "scale")).isFalse();
        }

        @Test
        void unknownTier_false() {
            assertThat(PpmTierRegistry.isEqualTier("growth", "unknown")).isFalse();
        }
    }
}
