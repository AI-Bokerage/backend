package io.genfin.api.internal.statemachine;

import io.genfin.api.statemachine.StateMachine;
import io.genfin.api.statemachine.Transition;
import io.genfin.api.statemachine.TransitionResult;
import io.genfin.api.statemachine.TransitionValidator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class DefaultStateMachine<S, E> implements StateMachine<S, E> {

  private final AtomicReference<S> current;
  private final List<Transition<S, E>> transitions;
  private final TransitionValidator<S, E> validator;

  public DefaultStateMachine(
      S initialState, List<Transition<S, E>> transitions, TransitionValidator<S, E> validator) {
    this.current = new AtomicReference<>(initialState);
    this.transitions = List.copyOf(transitions);
    this.validator = validator;
  }

  @Override
  public S currentState() {
    return current.get();
  }

  @Override
  public boolean canFire(E event) {
    return findTransition(current.get(), event).isPresent();
  }

  @Override
  public TransitionResult<S> fire(E event) {
    S from = current.get();
    var match = findTransition(from, event);
    if (match.isEmpty()) {
      return new TransitionResult.Rejected<>(
          from, "No transition defined for event " + event + " from state " + from);
    }
    Transition<S, E> transition = match.get();
    if (validator != null && !validator.isValid(from, event, transition.to())) {
      return new TransitionResult.Rejected<>(
          from, "Transition rejected by validator for event " + event);
    }
    current.set(transition.to());
    return new TransitionResult.Allowed<>(transition.to());
  }

  private java.util.Optional<Transition<S, E>> findTransition(S from, E event) {
    return transitions.stream()
        .filter(t -> t.from().equals(from) && t.event().equals(event))
        .findFirst();
  }
}
