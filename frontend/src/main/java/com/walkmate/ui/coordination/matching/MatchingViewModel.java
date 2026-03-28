package com.walkmate.ui.coordination.matching;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MatchingViewModel extends ViewModel {

    private static final long MATCH_DELAY_MS = 3000;

    private final MutableLiveData<MatchingUiState> uiState;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public MatchingViewModel(String hotspotName) {
        uiState = new MutableLiveData<>(MatchingUiState.initial(hotspotName));
        scheduleMatchComplete();
    }

    public LiveData<MatchingUiState> getUiState() {
        return uiState;
    }

    private void scheduleMatchComplete() {
        // TODO: Update to using GET("api/v1/intents/{intentId}/match")
        executor.execute(() -> {
            try {
                Thread.sleep(MATCH_DELAY_MS);
                MatchingUiState current = uiState.getValue();
                String name = current != null ? current.getHotspotName() : "";
                uiState.postValue(new MatchingUiState(name, true));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }
}
