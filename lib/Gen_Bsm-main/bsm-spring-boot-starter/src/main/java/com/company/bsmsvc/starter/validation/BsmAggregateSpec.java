package com.company.bsmsvc.starter.validation;

import java.util.List;

/**
 * Describes one bsm-core aggregate for {@link BsmPortAvailabilityValidator}: the anchor port(s)
 * that signal "this consumer wants this aggregate", the full port set the aggregate's
 * auto-configuration requires, and the service type(s) that should exist if wiring succeeded.
 */
record BsmAggregateSpec(
    String aggregateName,
    List<Class<?>> anchorPorts,
    List<Class<?>> requiredPorts,
    List<Class<?>> expectedServices
) {}
