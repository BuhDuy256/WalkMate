package com.walkmate.ui.rating;


public sealed interface RatingUiEvent permits
        RatingUiEvent.StarSelected,
        RatingUiEvent.TagToggled,
        RatingUiEvent.CommentChanged,
        RatingUiEvent.SubmitClicked,
        RatingUiEvent.CancelClicked {

    record StarSelected(int stars) implements RatingUiEvent {}
    record TagToggled(String tagCode) implements RatingUiEvent {}
    record CommentChanged(String comment) implements RatingUiEvent {}
    record SubmitClicked() implements RatingUiEvent {}
    record CancelClicked() implements RatingUiEvent {}
}
