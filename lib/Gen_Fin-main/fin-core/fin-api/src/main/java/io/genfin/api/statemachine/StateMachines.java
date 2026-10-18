package io.genfin.api.statemachine;

import io.genfin.api.internal.statemachine.DefaultStateMachine;
import java.util.List;

/** Factory for {@link StateMachine} instances. Consumers must obtain machines here. */
public final class StateMachines {

  private StateMachines() {}

  public static <S, E> StateMachine<S, E> of(S initialState, List<Transition<S, E>> transitions) {
    return new DefaultStateMachine<>(initialState, transitions, null);
  }

  public static <S, E> StateMachine<S, E> of(
      S initialState, List<Transition<S, E>> transitions, TransitionValidator<S, E> validator) {
    return new DefaultStateMachine<>(initialState, transitions, validator);
  }
}
