package io.genfin.api.statemachine;

public sealed interface TransitionResult<S> {

  S currentState();

  boolean isAllowed();

  record Allowed<S>(S currentState) implements TransitionResult<S> {
    @Override
    public boolean isAllowed() {
      return true;
    }
  }

  record Rejected<S>(S currentState, String reason) implements TransitionResult<S> {
    @Override
    public boolean isAllowed() {
      return false;
    }
  }
}
