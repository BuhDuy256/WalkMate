package com.walkingapp.ui.intent;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkingapp.data.repository.WalkingRepository;
import com.walkingapp.model.Intent;

/**
 * Refactored ViewModel using State Machine architecture.
 * 
 * KEY IMPROVEMENTS:
 * - Single source of truth: One LiveData<IntentScreenState>
 * - Impossible states prevented by design (sealed class hierarchy)
 * - Sequential workflow modeled internally (no UI coordination needed)
 * - Race condition protection (ignore actions while in-progress)
 * - Simpler UI: One observer, deterministic rendering
 */
public class IntentViewModelRefactored extends ViewModel {

  private final WalkingRepository repository;

  // SINGLE source of truth - UI observes only this
  private final MutableLiveData<IntentScreenState> screenState = new MutableLiveData<>();

  // Internal flag to prevent race conditions
  private boolean operationInProgress = false;

  public IntentViewModelRefactored() {
    this.repository = new WalkingRepository();
    // Set initial state
    screenState.setValue(IntentScreenState.Idle.INSTANCE);
  }

  /**
   * SINGLE LiveData exposed to UI - this is the only source of truth.
   * UI renders deterministically based on current state.
   */
  public LiveData<IntentScreenState> getScreenState() {
    return screenState;
  }

  /**
   * User action: Create intent and find match.
   * 
   * This method orchestrates the entire workflow:
   * 1. Create intent
   * 2. On success, automatically find match
   * 3. Emit appropriate terminal states
   * 
   * Race condition protection: Ignores action if operation already in progress.
   */
  public void createIntentAndFindMatch(int userId, String walkType, String startAt,
      int flexMinutes, double lat, double lng, int radiusM) {

    // PROTECTION: Prevent duplicate submissions while in progress
    if (operationInProgress) {
      android.util.Log.w("IntentViewModel", "Operation already in progress, ignoring duplicate action");
      return;
    }

    operationInProgress = true;

    // STATE TRANSITION: Idle → CreatingIntent
    screenState.setValue(IntentScreenState.CreatingIntent.INSTANCE);

    // Step 1: Create intent
    Intent intent = new Intent(walkType, startAt, flexMinutes, lat, lng, radiusM);
    intent.setUserId(userId);

    repository.createIntent(intent).observeForever(intentResult -> {
      if (intentResult == null)
        return;

      switch (intentResult.getStatus()) {
        case LOADING:
          // Already in CreatingIntent state, no action needed
          break;

        case SUCCESS:
          if (intentResult.getData() != null) {
            // STATE TRANSITION: CreatingIntent → FindingMatch
            screenState.setValue(IntentScreenState.FindingMatch.INSTANCE);

            // Step 2: Automatically find match (workflow continues)
            findMatchInternal(userId);
          } else {
            // Unexpected: success but no data
            transitionToError("Intent created but no data returned",
                IntentScreenState.Error.ErrorStage.INTENT_CREATION);
          }
          break;

        case ERROR:
          // STATE TRANSITION: CreatingIntent → Error
          transitionToError(
              intentResult.getError() != null ? intentResult.getError() : "Failed to create intent",
              IntentScreenState.Error.ErrorStage.INTENT_CREATION);
          break;
      }
    });
  }

  /**
   * Internal method to find match (called automatically after intent creation).
   * Not exposed to UI - this is part of the internal workflow.
   */
  private void findMatchInternal(int userId) {
    repository.findMatch(userId).observeForever(matchResult -> {
      if (matchResult == null)
        return;

      switch (matchResult.getStatus()) {
        case LOADING:
          // Already in FindingMatch state, no action needed break;

        case SUCCESS:
          if (matchResult.getData() != null) {
            // STATE TRANSITION: FindingMatch → MatchFound (TERMINAL STATE)
            screenState.setValue(new IntentScreenState.MatchFound(matchResult.getData()));
            operationInProgress = false; // Release lock
          } else {
            transitionToError("Match found but no data returned",
                IntentScreenState.Error.ErrorStage.MATCH_FINDING);
          }
          break;

        case ERROR:
          String errorMsg = matchResult.getError() != null ? matchResult.getError() : "Failed to find match";

          // Check if "no match found" vs actual error
          if (errorMsg.contains("No match found") || errorMsg.contains("No compatible")) {
            // STATE TRANSITION: FindingMatch → NoMatchFound (TERMINAL STATE)
            screenState.setValue(new IntentScreenState.NoMatchFound(errorMsg));
          } else {
            // STATE TRANSITION: FindingMatch → Error (TERMINAL STATE)
            transitionToError(errorMsg, IntentScreenState.Error.ErrorStage.MATCH_FINDING);
          }
          operationInProgress = false; // Release lock
          break;
      }
    });
  }

  /**
   * Helper to transition to error state and release lock.
   */
  private void transitionToError(String message, IntentScreenState.Error.ErrorStage stage) {
    screenState.setValue(new IntentScreenState.Error(message, stage));
    operationInProgress = false;
  }

  /**
   * User action: Reset to idle state (e.g., after error or no match, to try
   * again).
   */
  public void reset() {
    if (operationInProgress) {
      android.util.Log.w("IntentViewModel", "Cannot reset while operation in progress");
      return;
    }
    screenState.setValue(IntentScreenState.Idle.INSTANCE);
  }

  @Override
  protected void onCleared() {
    super.onCleared();
    // Clean up if needed (repository doesn't need cleanup in this case)
  }
}
