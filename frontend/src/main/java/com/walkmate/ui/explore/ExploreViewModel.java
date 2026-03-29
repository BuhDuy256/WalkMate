package com.walkmate.ui.explore;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.domain.hotspot.HotspotRepository;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.ui.explore.ExploreUiState.AppState;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for the Explore tab.
 *
 * Owns the hotspot list and the WELCOME → SETUP → SCANNING state machine.
 * All network/db work runs on the ExecutorService; results are posted back
 * to the main thread via LiveData.postValue().
 */
public class ExploreViewModel extends ViewModel {

    private final HotspotRepository hotspotRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<ExploreUiState> uiState =
            new MutableLiveData<>(ExploreUiState.initial());

    public ExploreViewModel(HotspotRepository hotspotRepository) {
        this.hotspotRepository = hotspotRepository;
        loadHotspots();
    }

    public LiveData<ExploreUiState> getUiState() {
        return uiState;
    }

    // ── Actions (called directly from ExploreFragment) ───────────────────

    public void loadHotspots() {
        ExploreUiState s = current();
        post(new ExploreUiState(true, s.getHotspots(), s.getSelectedHotspot(),
                s.getAppState(), null));

        hotspotRepository.getHotspots(new DomainCallback<List<Hotspot>>() {
            @Override
            public void onSuccess(List<Hotspot> hotspots) {
                post(new ExploreUiState(false, hotspots, null, AppState.WELCOME, null));
            }

            @Override
            public void onError(Exception error) {
                post(new ExploreUiState(false, current().getHotspots(), null,
                        AppState.WELCOME, error.getMessage()));
            }
        });
    }

    /**
     * User tapped a hotspot marker.
     * Transitions directly to SETUP (skipping the old HOTSPOT_SELECTED CTA-card step).
     */
    public void selectHotspot(String hotspotId) {
        ExploreUiState s = current();
        Hotspot found = null;
        for (Hotspot h : s.getHotspots()) {
            if (h.getId().equals(hotspotId)) {
                found = h;
                break;
            }
        }
        if (found == null) return;
        post(new ExploreUiState(false, s.getHotspots(), found, AppState.SETUP, null));
    }

    /**
     * User dismissed the create-intent form (back press or sheet drag-down).
     * Returns to WELCOME and clears the selected hotspot.
     * Wired by Phase B5.
     */
    public void closeSetup() {
        ExploreUiState s = current();
        if (s.getAppState() != AppState.SETUP) return;
        post(new ExploreUiState(false, s.getHotspots(), null, AppState.WELCOME, null));
    }

    /**
     * Create-intent form submitted successfully.
     * Moves to SCANNING state. The WalkIntent is passed to the Matches tab
     * via the shared repository; ExploreViewModel does not own the intent lifecycle.
     * Wired by Phase B5.
     */
    public void onIntentCreated(WalkIntent intent) {
        ExploreUiState s = current();
        // selectedHotspot is kept so the scanning card can show the hotspot name (Phase B6).
        post(new ExploreUiState(false, s.getHotspots(), s.getSelectedHotspot(),
                AppState.SCANNING, null));
    }

    /**
     * Scanning card dismissed (timeout or user action) → return to WELCOME.
     * Wired by Phase B6.
     */
    public void resetToWelcome() {
        ExploreUiState s = current();
        post(new ExploreUiState(false, s.getHotspots(), null, AppState.WELCOME, null));
    }

    public void consumeError() {
        ExploreUiState s = current();
        post(new ExploreUiState(s.isLoading(), s.getHotspots(), s.getSelectedHotspot(),
                s.getAppState(), null));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private ExploreUiState current() {
        ExploreUiState s = uiState.getValue();
        return s != null ? s : ExploreUiState.initial();
    }

    private void post(ExploreUiState state) {
        uiState.postValue(state);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }
}
