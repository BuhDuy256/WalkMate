package com.walkmate.ui.qr.show;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walksession.WalkSessionRepository;

public class ShowQrViewModel extends ViewModel {

    private final WalkSessionRepository            repository;
    private final MutableLiveData<ShowQrUiState>   uiState = new MutableLiveData<>();

    public ShowQrViewModel(WalkSessionRepository repository) {
        this.repository = repository;
    }

    public LiveData<ShowQrUiState> getUiState() { return uiState; }

    public void loadQrToken(String sessionId) {
        uiState.setValue(ShowQrUiState.loading());
        repository.fetchQrToken(sessionId, new DomainCallback<String>() {
            @Override
            public void onSuccess(String token) {
                uiState.postValue(ShowQrUiState.success(token));
            }

            @Override
            public void onError(Exception e) {
                uiState.postValue(ShowQrUiState.error(e.getMessage()));
            }
        });
    }

    public void consumeError() {
        ShowQrUiState current = uiState.getValue();
        if (current != null && current.getError() != null) {
            uiState.setValue(new ShowQrUiState(false, current.getQrToken(), null));
        }
    }
}
