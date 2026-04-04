package com.walkmate.ui.explore;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.walkmate.core.event.AppEvent;
import com.walkmate.core.event.AppEventBus;
import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.domain.hotspot.HotspotRepository;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import com.walkmate.ui.explore.ExploreUiState.AppState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for the Explore tab.
 *
 * Owns the hotspot list and the WELCOME → SETUP → SCANNING state machine.
 * All network/db work runs on the ExecutorService; results are posted back
 * to the main thread via LiveData.postValue().
 *
 * Phase 4 additions:
 *  - Stores activeIntentId to correlate FCM match events with the current scan.
 *  - 10-second Handler timeout triggers a "Still looking…" dialog signal.
 *  - Observes AppEventBus for MATCH_FOUND events (using observeForever so it
 *    works regardless of which Fragment is currently visible).
 *  - stopSearching() cancels the intent on the backend before resetting to WELCOME.
 */
public class ExploreViewModel extends ViewModel {

    private static final long TIMEOUT_MS = 10_000L;

    private final HotspotRepository hotspotRepository;
    private final WalkIntentRepository intentRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<ExploreUiState> uiState =
            new MutableLiveData<>(ExploreUiState.initial());

    // ── Scanning lifecycle ────────────────────────────────────────────────
    private String activeIntentId = null;

    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());

    private final Runnable timeoutRunnable = () -> {
        if (current().getAppState() == AppState.SCANNING) {
            post(current().withScanTimedOut(true));
        }
    };

    // ── FCM observer ─────────────────────────────────────────────────────
    private final Observer<AppEvent> appEventObserver = event -> {
        if (event == null) return;
        if (event.type == AppEvent.Type.MATCH_FOUND
                && event.intentId != null
                && event.intentId.equals(activeIntentId)) {
            cancelScanningTimeout();
            post(current().withMatchFound(event.proposalId));
            AppEventBus.get().consumeEvent();
        }
    };

    public ExploreViewModel(HotspotRepository hotspotRepository,
                            WalkIntentRepository intentRepository) {
        this.hotspotRepository = hotspotRepository;
        this.intentRepository  = intentRepository;
        AppEventBus.get().observe().observeForever(appEventObserver);
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
     * Filters the visible hotspot list by name substring (case-insensitive).
     * Passing null or empty string resets the filter to show all hotspots.
     * Only updates filteredHotspots — the full hotspots list is never mutated.
     */
    public void filterHotspots(String query) {
        ExploreUiState s = current();
        List<Hotspot> all = s.getHotspots();
        if (query == null || query.isEmpty()) {
            post(s.withFilteredHotspots(all));
            return;
        }
        String lower = query.toLowerCase();
        List<Hotspot> filtered = new ArrayList<>();
        for (Hotspot h : all) {
            if (h.getName().toLowerCase().contains(lower)) filtered.add(h);
        }
        post(s.withFilteredHotspots(filtered));
    }

    /**
     * User dismissed the create-intent form (back press or sheet drag-down).
     * Returns to WELCOME and clears the selected hotspot.
     */
    public void closeSetup() {
        ExploreUiState s = current();
        if (s.getAppState() != AppState.SETUP) return;
        post(new ExploreUiState(false, s.getHotspots(), null, AppState.WELCOME, null));
    }

    /**
     * Create-intent form submitted successfully.
     * Stores the intent ID for FCM correlation, starts the scanning timeout,
     * then moves to SCANNING state.
     */
    public void onIntentCreated(WalkIntent intent) {
        activeIntentId = intent.getId();
        ExploreUiState s = current();
        post(new ExploreUiState(false, s.getHotspots(), s.getSelectedHotspot(),
                AppState.SCANNING, null));
        startScanningTimeout();
    }

    /**
     * User tapped "Stop Searching".
     * Cancels the timeout, sends a cancel request to the backend, then resets to WELCOME.
     */
    public void stopSearching() {
        cancelScanningTimeout();
        if (activeIntentId != null) {
            final String idToCancel = activeIntentId;
            activeIntentId = null;
            intentRepository.cancelIntent(idToCancel, new DomainCallback<Void>() {
                @Override public void onSuccess(Void result) { /* no-op */ }
                @Override public void onError(Exception error) { /* silent; user already left scanning */ }
            });
        }
        resetToWelcome();
    }

    /**
     * Scanning card dismissed (timeout or user action) → return to WELCOME.
     */
    public void resetToWelcome() {
        ExploreUiState s = current();
        post(new ExploreUiState(false, s.getHotspots(), null, AppState.WELCOME, null));
    }

    /**
     * Resets scanTimedOut to false so the "Still looking…" dialog does not
     * re-appear on configuration change.
     */
    public void dismissTimeout() {
        post(current().withScanTimedOut(false));
    }

    /**
     * Called by ExploreFragment after it has handled the matchFoundProposalId navigation.
     * Clears the signal so it is not re-delivered on rotation.
     */
    public void consumeMatchFound() {
        ExploreUiState s = current();
        if (s.getMatchFoundProposalId() == null) return;
        post(new ExploreUiState(false, s.getHotspots(), s.getSelectedHotspot(),
                AppState.SCANNING, null));
    }

    public void consumeError() {
        ExploreUiState s = current();
        post(new ExploreUiState(s.isLoading(), s.getHotspots(), s.getSelectedHotspot(),
                s.getAppState(), null));
    }

    // ── Timeout helpers ───────────────────────────────────────────────────

    private void startScanningTimeout() {
        timeoutHandler.removeCallbacks(timeoutRunnable);
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);
    }

    private void cancelScanningTimeout() {
        timeoutHandler.removeCallbacks(timeoutRunnable);
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
        cancelScanningTimeout();
        AppEventBus.get().observe().removeObserver(appEventObserver);
        executor.shutdownNow();
        super.onCleared();
    }
}
