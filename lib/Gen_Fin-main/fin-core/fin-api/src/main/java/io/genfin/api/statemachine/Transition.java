package io.genfin.api.statemachine;

/** A single allowed {@code from -> to} move triggered by {@code event}. */
public record Transition<S, E>(S from, E event, S to) {}
