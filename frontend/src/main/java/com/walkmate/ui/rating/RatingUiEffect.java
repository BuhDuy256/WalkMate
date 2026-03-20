package com.walkmate.ui.rating;

/**
 * One-time UI effects for Rating screen
 */
public sealed interface RatingUiEffect permits
        RatingUiEffect.NavigateToSuccess,
        RatingUiEffect.NavigateBack,
        RatingUiEffect.ShowToast {

    record NavigateToSuccess() implements RatingUiEffect {}
    record NavigateBack() implements RatingUiEffect {}
    record ShowToast(String message) implements RatingUiEffect {}
}
