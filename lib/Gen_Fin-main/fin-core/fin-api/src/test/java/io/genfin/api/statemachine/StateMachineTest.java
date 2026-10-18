package io.genfin.api.statemachine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StateMachineTest {

  enum State {
    DRAFT,
    OPEN,
    CLOSED
  }

  enum Event {
    SUBMIT,
    CLOSE
  }

  @Test
  void allowedTransitionMovesToNextState() {
    StateMachine<State, Event> machine =
        StateMachines.of(
            State.DRAFT,
            List.of(
                new Transition<>(State.DRAFT, Event.SUBMIT, State.OPEN),
                new Transition<>(State.OPEN, Event.CLOSE, State.CLOSED)));

    TransitionResult<State> result = machine.fire(Event.SUBMIT);

    assertThat(result.isAllowed()).isTrue();
    assertThat(machine.currentState()).isEqualTo(State.OPEN);
  }

  @Test
  void undefinedTransitionIsRejectedAndStateUnchanged() {
    StateMachine<State, Event> machine =
        StateMachines.of(
            State.DRAFT, List.of(new Transition<>(State.DRAFT, Event.SUBMIT, State.OPEN)));

    TransitionResult<State> result = machine.fire(Event.CLOSE);

    assertThat(result.isAllowed()).isFalse();
    assertThat(machine.currentState()).isEqualTo(State.DRAFT);
  }

  @Test
  void validatorCanVetoAnOtherwiseAllowedTransition() {
    TransitionValidator<State, Event> alwaysReject = (from, event, to) -> false;
    StateMachine<State, Event> machine =
        StateMachines.of(
            State.DRAFT,
            List.of(new Transition<>(State.DRAFT, Event.SUBMIT, State.OPEN)),
            alwaysReject);

    assertThat(machine.canFire(Event.SUBMIT)).isTrue();
    assertThat(machine.fire(Event.SUBMIT).isAllowed()).isFalse();
    assertThat(machine.currentState()).isEqualTo(State.DRAFT);
  }
}
