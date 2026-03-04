package com.walkingapp.ui.intent;

import com.walkingapp.model.Proposal;

/**
 * Sealed class representing the exclusive states of the Intent & Match screen.
 * Impossible state combinations are prevented by design.
 */
public abstract class IntentScreenState {

  private IntentScreenState() {
  } // Prevent external inheritance

  // ===== Exclusive Screen States =====

  /** Initial state - form ready, waiting for user input */
  public static class Idle extends IntentScreenState {
    public static final Idle INSTANCE = new Idle();

    private Idle() {
    }
  }

  /** Creating intent on server - button disabled */
  public static class CreatingIntent extends IntentScreenState {
    public static final CreatingIntent INSTANCE = new CreatingIntent();

    private CreatingIntent() {
    }
  }

  /** Intent created successfully, now finding match - button still disabled */
  public static class FindingMatch extends IntentScreenState {
    public static final FindingMatch INSTANCE = new FindingMatch();

    private FindingMatch() {
    }
  }

  /** Match found successfully - show success dialog with proposal */
  public static class MatchFound extends IntentScreenState {
    private final Proposal proposal;

    public MatchFound(Proposal proposal) {
      this.proposal = proposal;
    }

    public Proposal getProposal() {
      return proposal;
    }
  }

  /** No match available - can retry */
  public static class NoMatchFound extends IntentScreenState {
    private final String message;

    public NoMatchFound(String message) {
      this.message = message;
    }

    public String getMessage() {
      return message;
    }
  }

  /** Error occurred (at any stage) - can retry */
  public static class Error extends IntentScreenState {
    private final String errorMessage;
    private final ErrorStage stage;

    public enum ErrorStage {
      INTENT_CREATION,
      MATCH_FINDING
    }

    public Error(String errorMessage, ErrorStage stage) {
      this.errorMessage = errorMessage;
      this.stage = stage;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public ErrorStage getStage() {
      return stage;
    }
  }
}
