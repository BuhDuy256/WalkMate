package com.walkmate.ui.rating;


public sealed interface RatingUiEffect permits
        RatingUiEffect.NavigateToSuccess,
        RatingUiEffect.NavigateBack,
        RatingUiEffect.ShowToast {

    record NavigateToSuccess() implements RatingUiEffect {}
    record NavigateBack() implements RatingUiEffect {}
    record ShowToast(String message) implements RatingUiEffect {}
}
