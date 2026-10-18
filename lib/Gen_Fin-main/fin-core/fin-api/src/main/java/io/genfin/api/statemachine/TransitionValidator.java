package io.genfin.api.statemachine;

/** Extra guard evaluated before an otherwise-allowed transition is applied. */
public interface TransitionValidator<S, E> {

  boolean isValid(S from, E event, S to);
}
