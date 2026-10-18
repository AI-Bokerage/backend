package io.genfin.api.statemachine;

/** Generic finite-state-machine contract. Implementations are not business-domain specific. */
public interface StateMachine<S, E> {

  S currentState();

  boolean canFire(E event);

  TransitionResult<S> fire(E event);
}
