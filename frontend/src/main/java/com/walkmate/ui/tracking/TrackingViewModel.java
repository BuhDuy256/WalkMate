package com.walkmate.ui.tracking;

import android.app.Application;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.maps.model.LatLng;
import com.walkmate.WalkMateApplication;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.tracking.RoutePoint;
import com.walkmate.domain.tracking.TrackingRepository;
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
 *                       [finishWalk]
 *                            │
 *                            ▼
 *                        FINISHED
 * </pre>
 *
 * <p><b>Thread safety:</b> All {@link MutableLiveData#setValue} calls happen
 * on the main thread. Timer ticks use {@link MutableLiveData#postValue} from
 * the {@link ScheduledExecutorService} background thread.
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

    // ── Completion error event ────────────────────────────────────────────────

    private final MutableLiveData<String> completionErrorLiveData = new MutableLiveData<>();

    public LiveData<String> getCompletionError() { return completionErrorLiveData; }

    // ── Repository + route cache ──────────────────────────────────────────────

    private final TrackingRepository repository;
    private final WalkSessionRepository sessionRepository;

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
                             @NonNull WalkSessionRepository sessionRepository) {
        super(application);
        repository = ((WalkMateApplication) application).getTrackingRepository();
        this.sessionRepository = sessionRepository;

        // Wire the two always-present sources; route points are added lazily.
        uiStateLiveData.addSource(walkStateLiveData,     state -> rebuildUiState());
        uiStateLiveData.addSource(elapsedSecondsLiveData, secs -> rebuildUiState());

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
     */
    public void startTrackingSession(
            String sessionId, String partnerId, String partnerName,
            double meetingLat, double meetingLng) {

        this.sessionId   = sessionId;
        this.partnerId   = partnerId;
        this.partnerName = partnerName;
        this.meetingLat  = meetingLat;
        this.meetingLng  = meetingLng;

        if (routePointsLiveData == null) {
            routePointsLiveData = repository.getPointsForSession(sessionId);
            uiStateLiveData.addSource(routePointsLiveData, points -> {
                latestRoutePoints = points != null ? points : Collections.emptyList();
                rebuildUiState();
            });
        }

        rebuildUiState();
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
    }

    /**
     * Transitions any active state to {@code FINISHED}.
     * Stops the timer and GPS service. The Activity should call
     * {@link TrackingScreenActivity#finish()} after observing {@code FINISHED}.
     */
    public void finishWalk() {
        stopTimer();
        stopGpsService();
        walkStateLiveData.setValue(WalkState.FINISHED);
    }

    /**
     * Requests walk completion via the backend API.
     * Enforces the 5-minute minimum gate — posts remaining seconds to UiState if too early.
     * Transitions ACTIVE → FINISHING → FINISHED (or back to ACTIVE on error).
     */
    public void requestCompleteWalk() {
        if (walkStateLiveData.getValue() != WalkState.ACTIVE) return;
        long elapsed = valueOrDefault(elapsedSecondsLiveData, 0L);
        long minDurationSeconds = WalkSession.MINIMUM_WALK_DURATION_MINUTES * 60L;
        if (elapsed < minDurationSeconds) {
            // Gate not yet reached — UiState already shows remaining time via rebuildUiState()
            rebuildUiState();
            return;
        }
        stopTimer();
        stopGpsService();
        walkStateLiveData.setValue(WalkState.FINISHING);
        sessionRepository.completeSession(sessionId, new DomainCallback<WalkSession>() {
            @Override
            public void onSuccess(WalkSession result) {
                walkStateLiveData.postValue(WalkState.FINISHED);
            }

            @Override
            public void onError(Exception e) {
                walkStateLiveData.postValue(WalkState.ACTIVE);
                startTimer();
                startGpsService();
                completionErrorLiveData.postValue(e.getMessage());
            }
        });
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
        long minDuration = WalkSession.MINIMUM_WALK_DURATION_MINUTES * 60L;
        long completeTooEarlySeconds = (state == WalkState.ACTIVE && elapsedSeconds < minDuration)
                ? (minDuration - elapsedSeconds) : 0L;

        boolean isSaving = (state == WalkState.FINISHING);

        uiStateLiveData.setValue(new TrackingUiState(
                state,
                mapPoints,
                distanceKm,
                elapsedSeconds,
                paceMinPerKm,
                partnerName,
                cameraFollowing,
                completeTooEarlySeconds,
                isSaving
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
        // Shutdown the executor so the background thread is released when the
        // ViewModel is cleared (e.g. back-stack pop, process death).
        timerExecutor.shutdown();
    }
}
