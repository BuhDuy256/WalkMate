package com.walkmate.ui.coordination;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.domain.hotspot.HotspotRepository;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.ui.coordination.CoordinationUiState.AppState;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CoordinationViewModel extends ViewModel {

    private final HotspotRepository hotspotRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<CoordinationUiState> uiState =
            new MutableLiveData<>(CoordinationUiState.initial());

    public CoordinationViewModel(HotspotRepository hotspotRepository) {
        this.hotspotRepository = hotspotRepository;
        loadHotspots();
    }

    public LiveData<CoordinationUiState> getUiState() {
        return uiState;
    }

    // ── Actions (called directly from Activity) ──────────────────────────

    public void loadHotspots() {
        CoordinationUiState s = current();
        post(new CoordinationUiState(true, s.getHotspots(), s.getSelectedHotspot(),
                s.getAppState(), s.getActiveIntent(), null));

        hotspotRepository.getHotspots(new DomainCallback<List<Hotspot>>() {
            @Override
            public void onSuccess(List<Hotspot> hotspots) {
                post(new CoordinationUiState(false, hotspots, null,
                        AppState.IDLE, null, null));
            }

            @Override
            public void onError(Exception error) {
                post(new CoordinationUiState(false, current().getHotspots(), null,
                        AppState.IDLE, null, error.getMessage()));
            }
        });
    }

    public void selectHotspot(String hotspotId) {
        CoordinationUiState s = current();
        Hotspot found = null;
        for (Hotspot h : s.getHotspots()) {
            if (h.getId().equals(hotspotId)) {
                found = h;
                break;
            }
        }
        if (found == null) return;
        post(new CoordinationUiState(false, s.getHotspots(), found,
                AppState.HOTSPOT_SELECTED, s.getActiveIntent(), null));
    }

    public void openCreateIntent() {
        CoordinationUiState s = current();
        if (s.getSelectedHotspot() == null) return;
        post(new CoordinationUiState(false, s.getHotspots(), s.getSelectedHotspot(),
                AppState.CREATE_INTENT, null, null));
    }

    public void closeCreateIntent() {
        CoordinationUiState s = current();
        if (s.getAppState() != AppState.CREATE_INTENT) return;
        post(new CoordinationUiState(false, s.getHotspots(), s.getSelectedHotspot(),
                AppState.HOTSPOT_SELECTED, null, null));
    }

    // Called by CoordinationActivity after CreateIntentViewModel successfully creates the intent
    public void onIntentCreated(WalkIntent intent) {
        CoordinationUiState s = current();
        post(new CoordinationUiState(false, s.getHotspots(), s.getSelectedHotspot(),
                AppState.MATCHING, intent, null));
    }

    public void onMatchTimerComplete() {
        CoordinationUiState s = current();
        post(new CoordinationUiState(false, s.getHotspots(), s.getSelectedHotspot(),
                AppState.MATCH_RESULT, s.getActiveIntent(), null));
    }

    public void resetToIdle() {
        CoordinationUiState s = current();
        post(new CoordinationUiState(false, s.getHotspots(), null,
                AppState.IDLE, null, null));
    }

    public void consumeError() {
        CoordinationUiState s = current();
        post(new CoordinationUiState(s.isLoading(), s.getHotspots(), s.getSelectedHotspot(),
                s.getAppState(), s.getActiveIntent(), null));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private CoordinationUiState current() {
        CoordinationUiState s = uiState.getValue();
        return s != null ? s : CoordinationUiState.initial();
    }

    private void post(CoordinationUiState state) {
        uiState.postValue(state);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }
}
