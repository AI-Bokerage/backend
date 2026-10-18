package com.company.ppmstarter;

import java.util.List;
import java.util.Set;

/**
 * Declares which repository port interfaces a single ppm-core aggregate's
 * use-case bean needs, so {@link PpmPortAvailabilityValidator} can detect a
 * partial implementation (some ports present, some missing) and fail fast
 * with a clear message instead of a later, unrelated-looking
 * {@code NoSuchBeanDefinitionException}.
 *
 * <p>{@code anchorPort} — the one port genuinely unique to this aggregate,
 * not shared with any independently-adoptable aggregate (e.g.
 * {@code PlanAddOnRepositoryPort} for PlanAddOn). Presence of the anchor is
 * what signals "this consumer wants this aggregate"; presence of a shared
 * port like {@code PlanRepositoryPort} does not, since nearly every
 * aggregate here depends on it and its presence alone says nothing about
 * intent toward any one of them. Requirements with no such unique port
 * (a composite spanning only already-independently-adoptable aggregates,
 * e.g. {@code CatalogQueryService}, {@code PromoValidationService}) are not
 * modeled here at all — see {@link PpmPortAvailabilityValidator}'s comments.
 */
record AggregatePortRequirement(String aggregateName, Class<?> anchorPort, List<Class<?>> requiredPorts) {

    List<Class<?>> missingFrom(Set<Class<?>> availablePortTypes) {
        return requiredPorts.stream().filter(port -> !availablePortTypes.contains(port)).toList();
    }

    boolean anchorPresent(Set<Class<?>> availablePortTypes) {
        return availablePortTypes.contains(anchorPort);
    }
}
