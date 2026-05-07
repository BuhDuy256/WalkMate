package com.walkmate.ui.tracking;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.maps.model.LatLng;
import com.walkmate.WalkMateApplication;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.tracking.PartnerOverlayState;
import com.walkmate.domain.tracking.PartnerPathResult;
import com.walkmate.domain.tracking.RoutePoint;
import com.walkmate.domain.tracking.TrackingRepository;
import com.walkmate.domain.tracking.TrackingRuntimeState;
import com.walkmate.domain.tracking.TrackingStateRepository;
import com.walkmate.domain.tracking.WalkState;
import com.walkmate.domain.walksession.WalkSession;
import com.walkmate.domain.walksession.WalkSessionRepository;
import com.walkmate.service.WalkTrackerService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * ViewModel for the GPS walk tracking screen.
 *
 * <p>Owns the walk state machine, the 1-second stopwatch, and the single
 * {@link TrackingUiState} snapshot delivered to the Activity via
 * {@link MediatorLiveData}. GPS service lifecycle (start/stop) is also
 * managed here so the Activity remains a passive view.
 *
 * <p><b>State machine:</b>
 * <pre>
 *   READY ──[startWalk]──► ACTIVE ──[pauseWalk]──► PAUSED
 *                            ▲                         │
 *                            └──────[resumeWalk]────────┘
 *                            │
 *                       [finishWalk / requestCompleteWalk]
 *                            │
 *                            ▼
 *                        FINISHED
 * </pre>
 *
 * <p><b>State persistence:</b> Every state transition persists timer epoch
 * values and walk state to Room via {@link TrackingStateRepository}. On
 * re-entry, {@link #startTrackingSession} loads the persisted state and
 * restores PAUSED or ACTIVE exactly — so leaving and returning to the
 * screen never resets an in-progress walk.
 *
 * <p><b>Thread safety:</b> All {@link MutableLiveData#setValue} calls happen
 * on the main thread. Timer ticks and Room callbacks use
 * {@link MutableLiveData#postValue} from background threads.
 */
public class TrackingViewModel extends AndroidViewModel {

    // ── Observable state ──────────────────────────────────────────────────────

    private final MutableLiveData<WalkState> walkStateLiveData =
            new MutableLiveData<>(WalkState.READY);

    private final MutableLiveData<Long> elapsedSecondsLiveData =
            new MutableLiveData<>(0L);

    private final MediatorLiveData<TrackingUiState> uiStateLiveData =
            new MediatorLiveData<>();

    // ── Session metadata (set once via startTrackingSession) ──────────────────

    private String sessionId;
    private String currentUserId;   // Logged-in user at the time the session was opened
    private String partnerId;
    private String partnerName;
    private double meetingLat;
    private double meetingLng;

    // ── Timer ─────────────────────────────────────────────────────────────────

    private final ScheduledExecutorService timerExecutor =
            Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> timerFuture;

    /** Epoch ms when the current walk (or resumed segment) started. */
    private long walkStartEpochMs;

    /** Total milliseconds spent in PAUSED state across all pause/resume cycles. */
    private long pausedAccumulatedMs;

    /** Epoch ms when the most recent pause began. 0 if not currently paused. */
    private long pauseStartEpochMs;

    // ── Partner path overlay ──────────────────────────────────────────────────

    /**
     * Signals new partner data (or null on poll failure) from the background
     * polling thread to the main-thread MediatorLiveData pipeline.
     * Using LiveData instead of direct field mutation ensures all partner state
     * updates happen on the main thread through the addSource observer.
     */
    private final MutableLiveData<PartnerPathResult> partnerResultLiveData =
            new MutableLiveData<>();

    /** In-memory accumulation of all decoded partner LatLng points. Main-thread only. */
    private final List<LatLng> partnerAccumulatedPoints = new ArrayList<>();
    private int    lastFetchedPartnerChunkIndex = -1;
    private long   partnerLastChunkMs           = 0L;
    /** Last known personal status of the partner: "PENDING" / "ACTIVE" / "COMPLETED" / "NO_SHOW". */
    private String partnerPersonalStatus        = "PENDING";

    private final ScheduledExecutorService partnerPollExecutor =
            Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> partnerPollFuture;

    // ── Completion error event ────────────────────────────────────────────────

    private final MutableLiveData<String> completionErrorLiveData = new MutableLiveData<>();

    public LiveData<String> getCompletionError() { return completionErrorLiveData; }

    // ── Repositories + route cache ────────────────────────────────────────────

    private final TrackingRepository repository;
    private final WalkSessionRepository sessionRepository;
    private final TrackingStateRepository stateRepository;

    /**
     * Room-backed LiveData for the current session's route points.
     * Null until {@link #startTrackingSession} is called for the first time.
     */
    private LiveData<List<RoutePoint>> routePointsLiveData;

    /**
     * Latest snapshot of route points cached so that non-route sources
     * (timer, walk state) can trigger a UI rebuild without requiring the
     * route LiveData to re-emit.
     */
    private List<RoutePoint> latestRoutePoints = Collections.emptyList();

    // ── Constructor ───────────────────────────────────────────────────────────

    public TrackingViewModel(@NonNull Application application,
                             @NonNull WalkSessionRepository sessionRepository,
                             @NonNull TrackingStateRepository stateRepository) {
        super(application);
        repository           = ((WalkMateApplication) application).getTrackingRepository();
        this.sessionRepository = sessionRepository;
        this.stateRepository   = stateRepository;

        // Wire always-present sources; route points and partner results are added lazily/here.
        uiStateLiveData.addSource(walkStateLiveData,      state  -> rebuildUiState());
        uiStateLiveData.addSource(elapsedSecondsLiveData, secs   -> rebuildUiState());
        uiStateLiveData.addSource(partnerResultLiveData,  result -> {
            // This observer runs on the main thread — safe to mutate partner state fields.
            if (result != null) {
                partnerPersonalStatus = result.getPartnerStatus();
                if (!result.getNewPoints().isEmpty()) {
                    partnerAccumulatedPoints.addAll(result.getNewPoints());
                    lastFetchedPartnerChunkIndex = result.getLastChunkIndex();
                    partnerLastChunkMs           = result.getLastChunkCreatedAtMs();
                }
            }
            rebuildUiState();
        });

        // Emit an initial non-null snapshot so the Activity never observes null.
        rebuildUiState();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public LiveData<TrackingUiState> getUiState() {
        return uiStateLiveData;
    }

    /**
     * Must be called from {@link TrackingScreenActivity#onCreate} after the
     * ViewModel is obtained. Idempotent — safe to call again on configuration
     * change because {@link TrackingRepository#getPointsForSession} returns the
     * same Room-backed LiveData instance and we guard against double-adding the
     * source.
     *
     * <p>Loads persisted runtime state from Room. If a PAUSED or ACTIVE record
     * is found for this session the ViewModel restores timer epoch values and
     * walk state instead of starting fresh at READY.
     */
    public void startTrackingSession(
            String sessionId, String partnerId, String partnerName,
            double meetingLat, double meetingLng) {

        this.sessionId   = sessionId;
        this.partnerId   = partnerId;
        this.partnerName = partnerName;
        this.meetingLat  = meetingLat;
        this.meetingLng  = meetingLng;

        // Capture the user ID once so all subsequent state reads/writes are
        // scoped to the account that was logged in when the session opened.
        // This prevents cross-account data leaks on a shared device.
        if (currentUserId == null) {
            WalkMateApplication app = (WalkMateApplication) getApplication();
            currentUserId = app.getSessionManager().getUserId();
        }

        if (routePointsLiveData == null) {
            routePointsLiveData = repository.getPointsForSession(sessionId);
            uiStateLiveData.addSource(routePointsLiveData, points -> {
                latestRoutePoints = points != null ? points : Collections.emptyList();
                rebuildUiState();
            });
        }

        // Load persisted state asynchronously and apply it.
        stateRepository.loadState(sessionId, currentUserId, new DomainCallback<TrackingRuntimeState>() {
            @Override
            public void onSuccess(TrackingRuntimeState saved) {
                applyRestoredState(saved); // safe to call from background thread
            }

            @Override
            public void onError(Exception e) {
                // Load failed — keep default READY state, do not block the user.
                walkStateLiveData.postValue(WalkState.READY);
            }
        });
    }

    /**
     * Transitions {@code READY → ACTIVE}.
     * Starts the GPS foreground service and the 1-second stopwatch.
     * No-op if already ACTIVE or beyond.
     */
    public void startWalk() {
        if (walkStateLiveData.getValue() != WalkState.READY) return;

        walkStartEpochMs    = System.currentTimeMillis();
        pausedAccumulatedMs = 0L;
        pauseStartEpochMs   = 0L;

        startGpsService();
        walkStateLiveData.setValue(WalkState.ACTIVE);
        startTimer();
        startPartnerPolling();

        persistState(WalkState.ACTIVE);
    }

    /**
     * Transitions {@code ACTIVE → PAUSED}.
     * Records the pause epoch, freezes the timer, and stops the GPS service
     * so no spurious points are recorded while paused.
     */
    public void pauseWalk() {
        if (walkStateLiveData.getValue() != WalkState.ACTIVE) return;

        pauseStartEpochMs = System.currentTimeMillis();
        stopTimer();
        stopGpsService();
        walkStateLiveData.setValue(WalkState.PAUSED);

        persistState(WalkState.PAUSED);
    }

    /**
     * Transitions {@code PAUSED → ACTIVE}.
     * Folds the paused duration into {@code pausedAccumulatedMs} so elapsed
     * time counts only active walking, then restarts GPS and the timer.
     */
    public void resumeWalk() {
        if (walkStateLiveData.getValue() != WalkState.PAUSED) return;

        pausedAccumulatedMs += System.currentTimeMillis() - pauseStartEpochMs;
        pauseStartEpochMs    = 0L;

        startGpsService();
        walkStateLiveData.setValue(WalkState.ACTIVE);
        startTimer();

        persistState(WalkState.ACTIVE);
    }

    /**
     * Transitions any active state to {@code FINISHED}.
     * Stops the timer and GPS service. The Activity should call
     * {@link TrackingScreenActivity#finish()} after observing {@code FINISHED}.
     */
    public void finishWalk() {
        stopTimer();
        stopPartnerPolling();
        stopGpsService();
        walkStateLiveData.setValue(WalkState.FINISHED);
        clearPersistedState();
    }

    /**
     * Requests walk completion via the backend API.
     *
     * <p>Enforces the minimum gate ({@link WalkSession#MINIMUM_WALK_DURATION_SECONDS}) —
     * posts remaining seconds to UiState if too early.
     *
     * <p>R1 — Final flush: stops the GPS service, then forces a synchronous push of
     * every remaining unsynced Room point before calling {@code completeSession()}.
     * This guarantees no GPS data is stranded locally after the backend marks the
     * session COMPLETED (which would reject any future sync calls for that session).
     * If the flush itself fails the walk is still completed — the user must not be
     * stuck on the FINISHING screen due to a network error.
     *
     * <p>State flow: ACTIVE|PAUSED → FINISHING → FINISHED (or back on error).
     */
    public void requestCompleteWalk() {
        WalkState currentState = valueOrDefault(walkStateLiveData, WalkState.READY);
        if (currentState != WalkState.ACTIVE && currentState != WalkState.PAUSED) return;

        long elapsed = valueOrDefault(elapsedSecondsLiveData, 0L);
        long minDurationSeconds = WalkSession.MINIMUM_WALK_DURATION_SECONDS;
        if (elapsed < minDurationSeconds) {
            rebuildUiState();
            return;
        }

        stopTimer();
        stopPartnerPolling();
        stopGpsService();
        walkStateLiveData.setValue(WalkState.FINISHING);

        // R1: flush all remaining unsynced Room points before completing on the backend.
        repository.flushUnsyncedBeforeComplete(sessionId, new DomainCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                completeOnBackend(currentState);
            }

            @Override
            public void onError(Exception e) {
                // Flush failed (e.g. network down) — still complete the session so the
                // user is not stranded indefinitely on the FINISHING screen.
                Log.w("TrackingViewModel",
                        "Pre-complete flush failed, proceeding anyway: " + e.getMessage());
                completeOnBackend(currentState);
            }
        });
    }

    /**
     * Calls the backend to mark the session COMPLETED.
     * On error the ViewModel reverts to {@code previousState} so the user can retry.
     */
    private void completeOnBackend(WalkState previousState) {
        sessionRepository.completeSession(sessionId, new DomainCallback<WalkSession>() {
            @Override
            public void onSuccess(WalkSession result) {
                clearPersistedState();
                walkStateLiveData.postValue(WalkState.FINISHED);
            }

            @Override
            public void onError(Exception e) {
                walkStateLiveData.postValue(previousState);
                if (previousState == WalkState.ACTIVE) {
                    startTimer();
                    startGpsService();
                }
                completionErrorLiveData.postValue(e.getMessage());
            }
        });
    }

    // ── State restore ─────────────────────────────────────────────────────────

    /**
     * Called from a background thread after Room returns the persisted state.
     * Uses {@code postValue} for LiveData and relies on the fact that timer
     * fields are written before any timer task is scheduled (happens-before).
     */
    private void applyRestoredState(TrackingRuntimeState saved) {
        if (saved == null) {
            // No record — fresh session, keep default READY.
            return;
        }

        WalkState savedState = saved.getWalkState();

        if (savedState == WalkState.PAUSED) {
            walkStartEpochMs    = saved.getWalkStartEpochMs();
            pausedAccumulatedMs = saved.getPausedAccumulatedMs();
            pauseStartEpochMs   = saved.getPauseStartEpochMs();

            // Elapsed = time from start to when it was paused, minus earlier pauses.
            long activeMs = (pauseStartEpochMs - walkStartEpochMs) - pausedAccumulatedMs;
            elapsedSecondsLiveData.postValue(Math.max(0L, activeMs / 1000L));
            walkStateLiveData.postValue(WalkState.PAUSED);

        } else if (savedState == WalkState.ACTIVE) {
            walkStartEpochMs    = saved.getWalkStartEpochMs();
            pausedAccumulatedMs = saved.getPausedAccumulatedMs();
            pauseStartEpochMs   = 0L;

            // Restart GPS (may already be running as a foreground service; re-delivering
            // the intent is harmless) and the live timer.
            startGpsService();
            walkStateLiveData.postValue(WalkState.ACTIVE);
            startTimer();
            startPartnerPolling();

        }
        // READY / FINISHING / FINISHED → no restore; use default READY.
    }

    // ── State persistence helpers ─────────────────────────────────────────────

    /**
     * Fire-and-forget persist of the current timer epoch values + given state.
     * Errors are silently swallowed — a failed persist is non-fatal; the worst
     * outcome is losing resume state, which is the same as the old behaviour.
     */
    private void persistState(WalkState state) {
        if (sessionId == null) return;
        TrackingRuntimeState snapshot = new TrackingRuntimeState(
                sessionId,
                currentUserId,
                state,
                walkStartEpochMs,
                pausedAccumulatedMs,
                pauseStartEpochMs,
                System.currentTimeMillis()
        );
        stateRepository.saveState(snapshot, new DomainCallback<Void>() {
            @Override public void onSuccess(Void result) { /* no-op */ }
            @Override public void onError(Exception e)   { /* non-fatal, swallow */ }
        });
    }

    /**
     * Removes the state record when the session ends so a future screen open
     * does not accidentally restore a finished walk.
     */
    private void clearPersistedState() {
        if (sessionId == null) return;
        stateRepository.deleteState(sessionId, currentUserId, new DomainCallback<Void>() {
            @Override public void onSuccess(Void result) { /* no-op */ }
            @Override public void onError(Exception e)   { /* non-fatal, swallow */ }
        });
    }

    // ── Partner path polling ──────────────────────────────────────────────────

    private void startPartnerPolling() {
        stopPartnerPolling(); // cancel any stale future first
        partnerPollFuture = partnerPollExecutor.scheduleAtFixedRate(
                this::pollPartnerPath, 3L, 8L, TimeUnit.SECONDS);
    }

    private void stopPartnerPolling() {
        if (partnerPollFuture != null && !partnerPollFuture.isCancelled()) {
            partnerPollFuture.cancel(false);
            partnerPollFuture = null;
        }
    }

    private void pollPartnerPath() {
        final String sid = sessionId;
        if (sid == null) return;

        repository.fetchPartnerPath(sid, lastFetchedPartnerChunkIndex,
                new DomainCallback<PartnerPathResult>() {
                    @Override
                    public void onSuccess(PartnerPathResult result) {
                        // postValue marshals to the main thread; the addSource observer
                        // in the constructor handles the actual state mutation.
                        partnerResultLiveData.postValue(result);
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.w("TrackingViewModel", "Partner poll failed: " + e.getMessage());
                        if (e.getMessage() != null
                                && e.getMessage().startsWith("SESSION_TERMINAL|")) {
                            stopPartnerPolling();
                        }
                        // Do not clear partner data — keep last known path visible.
                        // The 1-second timer keeps incrementing partnerLastUpdatedSeconds
                        // via the normal rebuildUiState() cadence.
                    }
                });
    }

    private PartnerOverlayState computePartnerOverlayState() {
        if ("COMPLETED".equals(partnerPersonalStatus) || "NO_SHOW".equals(partnerPersonalStatus)) {
            return PartnerOverlayState.PARTNER_COMPLETED;
        }
        if ("ACTIVE".equals(partnerPersonalStatus) && !partnerAccumulatedPoints.isEmpty()) {
            return PartnerOverlayState.SHOWING_PATH;
        }
        if ("ACTIVE".equals(partnerPersonalStatus)) {
            return PartnerOverlayState.WAITING_FOR_GPS;
        }
        return PartnerOverlayState.WAITING_FOR_PARTNER;
    }

    // ── Timer ─────────────────────────────────────────────────────────────────

    private void startTimer() {
        stopTimer(); // cancel stale future before creating a new one
        timerFuture = timerExecutor.scheduleAtFixedRate(() -> {
            long activeMs = (System.currentTimeMillis() - walkStartEpochMs)
                    - pausedAccumulatedMs;
            // postValue is safe from the background timer thread
            elapsedSecondsLiveData.postValue(Math.max(0L, activeMs / 1000L));
        }, 0L, 1L, TimeUnit.SECONDS);
    }

    private void stopTimer() {
        if (timerFuture != null && !timerFuture.isCancelled()) {
            timerFuture.cancel(false); // let the in-flight tick finish; don't interrupt
            timerFuture = null;
        }
    }

    // ── GPS Service ───────────────────────────────────────────────────────────

    private void startGpsService() {
        Intent intent = buildServiceIntent();
        // ContextCompat handles the API 26 requirement for startForegroundService
        ContextCompat.startForegroundService(getApplication(), intent);
    }

    private void stopGpsService() {
        getApplication().stopService(
                new Intent(getApplication(), WalkTrackerService.class));
    }

    private Intent buildServiceIntent() {
        Intent intent = new Intent(getApplication(), WalkTrackerService.class);
        intent.putExtra(WalkTrackerService.EXTRA_SESSION_ID,   sessionId);
        intent.putExtra(WalkTrackerService.EXTRA_PARTNER_ID,   partnerId);
        intent.putExtra(WalkTrackerService.EXTRA_PARTNER_NAME, partnerName);
        intent.putExtra(WalkTrackerService.EXTRA_MEETING_LAT,  meetingLat);
        intent.putExtra(WalkTrackerService.EXTRA_MEETING_LNG,  meetingLng);
        return intent;
    }

    // ── MediatorLiveData recalculation ────────────────────────────────────────

    /**
     * Rebuilds and posts a new {@link TrackingUiState} from the current values
     * of all three sources. Called whenever any source emits.
     *
     * <p>All calls happen on the main thread (the two {@code addSource} lambdas
     * run on main; {@link MutableLiveData#postValue} from the timer triggers the
     * {@code elapsedSecondsLiveData} observer on main).
     */
    private void rebuildUiState() {
        WalkState state     = valueOrDefault(walkStateLiveData,      WalkState.READY);
        long elapsedSeconds = valueOrDefault(elapsedSecondsLiveData, 0L);

        List<LatLng> mapPoints = toLatLngList(latestRoutePoints);
        double distanceKm      = computeDistanceKm(latestRoutePoints);
        double paceMinPerKm    = computePace(distanceKm, elapsedSeconds);

        // Camera follows the user while actively walking; freezes on pause/finish.
        boolean cameraFollowing = (state == WalkState.ACTIVE);

        // Compute remaining seconds before complete is allowed (0 when permitted).
        long minDuration = WalkSession.MINIMUM_WALK_DURATION_SECONDS;
        boolean gatingState = (state == WalkState.ACTIVE || state == WalkState.PAUSED);
        long completeTooEarlySeconds = (gatingState && elapsedSeconds < minDuration)
                ? (minDuration - elapsedSeconds) : 0L;

        boolean isSaving = (state == WalkState.FINISHING);

        // Partner overlay — snapshot the accumulated list to avoid external mutation.
        List<LatLng> partnerSnapshot    = new ArrayList<>(partnerAccumulatedPoints);
        PartnerOverlayState overlayState = computePartnerOverlayState();
        long partnerLastUpdatedSecs     = (partnerLastChunkMs > 0)
                ? (System.currentTimeMillis() - partnerLastChunkMs) / 1000L : 0L;

        uiStateLiveData.setValue(new TrackingUiState(
                state,
                mapPoints,
                distanceKm,
                elapsedSeconds,
                paceMinPerKm,
                partnerName,
                cameraFollowing,
                completeTooEarlySeconds,
                isSaving,
                partnerSnapshot,
                overlayState,
                partnerLastUpdatedSecs
        ));
    }

    // ── Pure computation helpers (no Android imports) ─────────────────────────

    private static List<LatLng> toLatLngList(List<RoutePoint> points) {
        List<LatLng> result = new ArrayList<>(points.size());
        for (RoutePoint p : points) {
            result.add(new LatLng(p.getLat(), p.getLng()));
        }
        return result;
    }

    /**
     * Incremental Haversine sum over the ordered route point list.
     * Returns distance in kilometres.
     */
    private static double computeDistanceKm(List<RoutePoint> points) {
        if (points.size() < 2) return 0.0;
        double totalMeters = 0.0;
        for (int i = 1; i < points.size(); i++) {
            RoutePoint a = points.get(i - 1);
            RoutePoint b = points.get(i);
            totalMeters += haversineMeters(a.getLat(), a.getLng(),
                                           b.getLat(), b.getLng());
        }
        return totalMeters / 1000.0;
    }

    /**
     * Returns pace in min/km once the walker has covered at least 50 m.
     * Returns {@code 0.0} until that threshold is reached.
     */
    private static double computePace(double distanceKm, long elapsedSeconds) {
        if (distanceKm * 1000.0 < 50.0 || elapsedSeconds <= 0) return 0.0;
        return (elapsedSeconds / 60.0) / distanceKm;
    }

    /**
     * Haversine great-circle distance in metres between two WGS-84 coordinates.
     *
     * <p>Intentionally self-contained here — {@link com.walkmate.domain.tracking.LocationFilterPolicy}
     * has its own copy so the domain layer stays decoupled from the UI layer.
     */
    private static double haversineMeters(double lat1, double lng1,
                                          double lat2, double lng2) {
        final double R = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
    }

    private static <T> T valueOrDefault(MutableLiveData<T> ld, T fallback) {
        T v = ld.getValue();
        return v != null ? v : fallback;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCleared() {
        super.onCleared();
        stopTimer();
        stopPartnerPolling();
        timerExecutor.shutdown();
        partnerPollExecutor.shutdown();
    }
}
