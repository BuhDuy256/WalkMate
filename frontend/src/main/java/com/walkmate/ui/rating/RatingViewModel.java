package com.walkmate.ui.rating;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.rating.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class RatingViewModel extends ViewModel {
    private final RatingService ratingService;
    private final ExecutorService executorService;
    private final UUID currentUserId;

    private final MutableLiveData<RatingUiState> _uiState = new MutableLiveData<>();
    public final LiveData<RatingUiState> uiState = _uiState;

    private final MutableLiveData<RatingUiEffect> _uiEffect = new MutableLiveData<>();
    public final LiveData<RatingUiEffect> uiEffect = _uiEffect;

    public RatingViewModel(RatingService ratingService, RatingViewData initialData, UUID currentUserId) {
        this.ratingService = ratingService;
        this.executorService = Executors.newSingleThreadExecutor();
        this.currentUserId = currentUserId;
        _uiState.setValue(RatingUiState.initial(initialData));
    }

    public void onEvent(RatingUiEvent event) {
        if (event instanceof RatingUiEvent.StarSelected) {
            handleStarSelected(((RatingUiEvent.StarSelected) event).stars());
        } else if (event instanceof RatingUiEvent.TagToggled) {
            handleTagToggled(((RatingUiEvent.TagToggled) event).tagCode());
        } else if (event instanceof RatingUiEvent.CommentChanged) {
            handleCommentChanged(((RatingUiEvent.CommentChanged) event).comment());
        } else if (event instanceof RatingUiEvent.SubmitClicked) {
            handleSubmit();
        } else if (event instanceof RatingUiEvent.CancelClicked) {
            handleCancel();
        }
    }

    private void handleStarSelected(int stars) {
        RatingUiState currentState = _uiState.getValue();
        if (currentState == null) return;

        RatingViewData currentData = currentState.getData();
        RatingViewData updatedData = currentData.withStars(stars);
        updateState(updatedData);
    }

    private void handleTagToggled(String tagCode) {
        RatingUiState currentState = _uiState.getValue();
        if (currentState == null) return;

        RatingViewData currentData = currentState.getData();

        List<RatingViewData.TagViewData> updatedTags = new ArrayList<>();
        for (RatingViewData.TagViewData tag : currentData.getAvailableTags()) {
            if (tag.getCode().equals(tagCode)) {
                updatedTags.add(tag.withSelected(!tag.isSelected()));
            } else {
                updatedTags.add(tag);
            }
        }

        RatingViewData updatedData = currentData.withTags(updatedTags);
        updateState(updatedData);
    }

    private void handleCommentChanged(String comment) {
        RatingUiState currentState = _uiState.getValue();
        if (currentState == null) return;

        RatingViewData currentData = currentState.getData();
        RatingViewData updatedData = currentData.withComment(comment);
        updateState(updatedData);
    }

    private void updateState(RatingViewData newData) {
        RatingUiState currentState = _uiState.getValue();
        if (currentState == null) return;

        boolean canSubmit = newData.isValid();
        _uiState.setValue(currentState.withData(newData, canSubmit));
    }

    private void handleSubmit() {
        RatingUiState currentState = _uiState.getValue();
        if (currentState == null) return;

        RatingViewData data = currentState.getData();

        if (!data.isValid()) {
            _uiEffect.setValue(new RatingUiEffect.ShowToast("Please select a rating"));
            return;
        }

        _uiState.setValue(currentState.withLoading());

        Rating domainRating = buildDomainRating(data);

        executorService.execute(() -> {
            try {
                ratingService.submitRating(domainRating);

                _uiState.postValue(_uiState.getValue().withSuccess());
                _uiEffect.postValue(new RatingUiEffect.NavigateToSuccess());

            } catch (RatingException e) {
                String errorMessage = mapErrorToMessage(e.getErrorCode());
                _uiState.postValue(_uiState.getValue().withError(errorMessage));
                _uiEffect.postValue(new RatingUiEffect.ShowToast(errorMessage));
            }
        });
    }

    private Rating buildDomainRating(RatingViewData data) {
        UUID sessionId = data.getSessionId();
        UUID reviewerId = currentUserId;
        UUID revieweeId = data.getPartner().getUserId();

        RatingScore score = new RatingScore(data.getSelectedStars());

        List<RatingTag> tags = data.getSelectedTagsWithDbCode().stream()
                .map(tag -> RatingTag.fromCode(tag.getDbCode()))
                .collect(Collectors.toList());

        RatingComment comment = new RatingComment(data.getComment());

        return new Rating(sessionId, reviewerId, revieweeId, score, tags, comment);
    }

    private String mapErrorToMessage(RatingErrorCode errorCode) {
        return switch (errorCode) {
            case RATING_SESSION_NOT_COMPLETED -> "Session is not completed yet";
            case RATING_ALREADY_EXISTS -> "You have already rated this session";
            case RATING_INVALID_SCORE -> "Invalid rating score";
            case NETWORK_ERROR -> "Network error. Please try again";
            default -> "An error occurred. Please try again";
        };
    }

    private void handleCancel() {
        _uiEffect.setValue(new RatingUiEffect.NavigateBack());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}
