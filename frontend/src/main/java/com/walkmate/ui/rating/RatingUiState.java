package com.walkmate.ui.rating;

import java.util.HashSet;
import java.util.Set;

public class RatingUiState {
    public final boolean isLoading;
    public final String errorMessage;
    public final boolean isSubmitSuccessful;

    // Display Models
    public final String partnerName;
    public final String walkDate;
    public final String duration;
    public final String distance;
    public final String steps;

    // Mutating Form State
    public final int currentRating;
    public final Set<String> selectedTags;
    public final String note;

    public RatingUiState(boolean isLoading, String errorMessage, boolean isSubmitSuccessful, 
                         String partnerName, String walkDate, String duration, String distance, String steps, 
                         int currentRating, Set<String> selectedTags, String note) {
        this.isLoading = isLoading;
        this.errorMessage = errorMessage;
        this.isSubmitSuccessful = isSubmitSuccessful;
        this.partnerName = partnerName;
        this.walkDate = walkDate;
        this.duration = duration;
        this.distance = distance;
        this.steps = steps;
        this.currentRating = currentRating;
        this.selectedTags = selectedTags;
        this.note = note;
    }

    // Standard constructor for idle/initial load
    public static RatingUiState initial(String partnerName, String walkDate, String duration, String distance, String steps) {
        return new RatingUiState(false, null, false, partnerName, walkDate, duration, distance, steps, 0, new HashSet<>(), "");
    }

    // Builder method to copy and mutate state immutably
    public RatingUiState copyWith(boolean isLoading, String errorMessage, boolean isSubmitSuccessful, 
                                  int currentRating, Set<String> selectedTags, String note) {
        return new RatingUiState(
                isLoading, errorMessage, isSubmitSuccessful,
                this.partnerName, this.walkDate, this.duration, this.distance, this.steps,
                currentRating, selectedTags, note
        );
    }
}
