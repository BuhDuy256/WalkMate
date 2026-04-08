package com.walkmate.ui.explore.createintent;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;

/**
 * Owns the Create Intent form submission logic.
 * Observed by ExploreFragment (not a sub-Fragment).
 */
public class CreateIntentViewModel extends ViewModel {

    private final WalkIntentRepository intentRepository;
    private final MutableLiveData<CreateIntentUiState> uiState =
            new MutableLiveData<>(CreateIntentUiState.initial());

    public CreateIntentViewModel(WalkIntentRepository intentRepository) {
        this.intentRepository = intentRepository;
    }

    public LiveData<CreateIntentUiState> getUiState() {
        return uiState;
    }

    public void submit(String hotspotId, String date, float timeStart, float timeEnd,
                       int ageMin, int ageMax, java.util.List<String> tags,
                       boolean isPrivate, String invitedFriendId) {
        uiState.setValue(new CreateIntentUiState(true, null, null));

        intentRepository.createIntent(hotspotId, date, timeStart, timeEnd, ageMin, ageMax, tags,
                isPrivate, invitedFriendId, null,
                new DomainCallback<WalkIntent>() {
                    @Override
                    public void onSuccess(WalkIntent intent) {
                        uiState.postValue(new CreateIntentUiState(false, null, intent));
                    }

                    @Override
                    public void onError(Exception error) {
                        uiState.postValue(
                                new CreateIntentUiState(false, error.getMessage(), null));
                    }
                });
    }

    public void consumeError() {
        CreateIntentUiState s = uiState.getValue();
        if (s != null) {
            uiState.setValue(new CreateIntentUiState(s.isLoading(), null, s.getSubmittedIntent()));
        }
    }
}
