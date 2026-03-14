package com.walkmate.ui.rating;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import java.util.HashSet;
import java.util.Set;

public class RatingViewModel extends ViewModel {

    private final MutableLiveData<RatingUiState> state = new MutableLiveData<>();
    // private final RatingRepository repository; // Injected via constructor or dependency injection

    public RatingViewModel() {
        // Initialize state mock data (this data would usually arrive via Intent args or a repository fetch)
        state.setValue(RatingUiState.initial("Sophia K.", "04 MAR", "32 min", "1.2 km", "1,580 steps"));
    }

    public LiveData<RatingUiState> getState() {
        return state;
    }

    public void updateRating(int rating) {
        RatingUiState current = state.getValue();
        if (current != null) {
            state.setValue(current.copyWith(current.isLoading, current.errorMessage, current.isSubmitSuccessful, rating, current.selectedTags, current.note));
        }
    }

    public void toggleTag(String tag) {
        RatingUiState current = state.getValue();
        if (current != null) {
            Set<String> updatedTags = new HashSet<>(current.selectedTags);
            if (updatedTags.contains(tag)) {
                updatedTags.remove(tag);
            } else {
                updatedTags.add(tag);
            }
            
            state.setValue(current.copyWith(current.isLoading, current.errorMessage, current.isSubmitSuccessful, current.currentRating, updatedTags, current.note));
        }
    }

    public void updateNote(String note) {
        RatingUiState current = state.getValue();
        if (current != null) {
            state.setValue(current.copyWith(current.isLoading, current.errorMessage, current.isSubmitSuccessful, current.currentRating, current.selectedTags, note));
        }
    }

    public void submitRating() {
        RatingUiState current = state.getValue();
        if (current == null || current.isLoading) return;

        // Transition to Loading State
        state.setValue(current.copyWith(true, null, false, current.currentRating, current.selectedTags, current.note));

        // Mocking Repository Call returning Result<T> as per FrontendDevelopmentGuide.md rules
        /* 
        repository.submitWalkRating(current.currentRating, current.selectedTags, current.note, result -> {
            if (result.isSuccess()) {
                 state.postValue(current.copyWith(false, null, true, current.currentRating, current.selectedTags, current.note));
            } else {
                 state.postValue(current.copyWith(false, result.getError().getMessage(), false, current.currentRating, current.selectedTags, current.note));
            }
        });
        */
        
        // Mock success delay
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(() -> {
            state.postValue(current.copyWith(false, null, true, current.currentRating, current.selectedTags, current.note));
        }, 1500);
    }
}
