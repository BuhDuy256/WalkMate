package com.walkmate.ui.coordination.matchresult;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class MatchResultViewModel extends ViewModel {

    private final MutableLiveData<MatchResultUiState> uiState =
            new MutableLiveData<>(MatchResultUiState.initial());

    public LiveData<MatchResultUiState> getUiState() {
        return uiState;
    }

    public void accept() {
        uiState.setValue(new MatchResultUiState(MatchResultUiState.Action.ACCEPTED));
    }

    public void pass() {
        uiState.setValue(new MatchResultUiState(MatchResultUiState.Action.PASSED));
    }
}
