package com.walkmate.ui.tracking;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.walkmate.R;
import com.walkmate.core.designsystem.view.AvatarInitialView;
import com.walkmate.ui.qr.QrVerifyActivity;
import com.walkmate.core.designsystem.view.WalkMateStatColumn;
import com.walkmate.domain.tracking.PartnerOverlayState;
import com.walkmate.domain.tracking.WalkState;
import com.walkmate.service.WalkTrackerService;
import com.walkmate.ui.gamification.PostSessionSummaryFragment;

import java.util.List;
import java.util.Locale;

/**
 * GPS Walk Tracking Screen — Phase 5 full UI.
 *
 * <p>Responsibilities (this Activity is a pure passive view):
 * <ul>
 *   <li>Renders {@link TrackingUiState} snapshots from {@link TrackingViewModel}.</li>
 *   <li>Draws and updates the route {@link Polyline} on the Google Map.</li>
 *   <li>Animates the camera to follow the walker when {@code isCameraFollowingUser} is true.</li>
 *   <li>Translates button clicks into ViewModel lifecycle commands.</li>
 *   <li>Requests {@code ACCESS_FINE_LOCATION} permission before allowing the walk to start.</li>
 * </ul>
 *
 * <p>All business logic — timer, pace, GPS service lifecycle — lives in
 * {@link TrackingViewModel}.
 */
public class TrackingScreenActivity extends AppCompatActivity implements OnMapReadyCallback {

    // ── Intent extra keys (re-exported from WalkTrackerService) ──────────────

    public static final String EXTRA_SESSION_ID   = WalkTrackerService.EXTRA_SESSION_ID;
    public static final String EXTRA_PARTNER_ID   = WalkTrackerService.EXTRA_PARTNER_ID;
    public static final String EXTRA_PARTNER_NAME = WalkTrackerService.EXTRA_PARTNER_NAME;
    public static final String EXTRA_MEETING_LAT  = WalkTrackerService.EXTRA_MEETING_LAT;
    public static final String EXTRA_MEETING_LNG  = WalkTrackerService.EXTRA_MEETING_LNG;
    public static final String EXTRA_HOTSPOT_NAME = "HOTSPOT_NAME";

    private static final String TAG = "TrackingScreenActivity";
    private static final int    REQUEST_LOCATION_PERMISSION = 100;
    private static final float  MAP_TRACKING_ZOOM   = 18.5f;
    private static final int    POLYLINE_COLOR       = 0xFFFF7B3A; // orange — own path
    private static final int    PARTNER_POLYLINE_COLOR = 0xFF4A90E2; // blue — partner path

    // ── Session contract ──────────────────────────────────────────────────────

    private String sessionId;
    private String partnerId;
    private String partnerName;
    private String hotspotName;
    private double meetingLat;
    private double meetingLng;

    // ── ViewModel ─────────────────────────────────────────────────────────────

    private TrackingViewModel viewModel;

    // ── Map ───────────────────────────────────────────────────────────────────

    private GoogleMap    googleMap;
    private Polyline     polyline;
    private Polyline     partnerPolyline;
    /** True after the camera has flown to the first GPS point. */
    private boolean      hasInitialCameraFly = false;

    // ── Location (for immediate zoom before first GPS fix from service) ────────

    private FusedLocationProviderClient fusedLocationClient;

    // ── Views ─────────────────────────────────────────────────────────────────

    private AvatarInitialView      avatarPartner;
    private TextView               txtPartnerNameSheet;
    private TextView               txtPartnerStatus;
    private WalkMateStatColumn     statDistance;
    private WalkMateStatColumn     statDuration;
    private WalkMateStatColumn     statPace;
    private MaterialButton         btnStart;
    private LinearLayout          btnRowPauseStop;
    private MaterialButton        btnPause;
    private LinearLayout          btnRowVerifyComplete;
    private MaterialButton        btnVerifyPartner;
    private MaterialButton        btnComplete;
    private FloatingActionButton  fabRecenter;
    private LinearLayout          bottomPanel;
    private LinearLayout          badgeLive;

    // ── Flags ─────────────────────────────────────────────────────────────────

    private boolean finishDialogShown = false;
    private int     bottomPanelHeightPx = 0;
    private boolean shouldStartWalkAfterPermission = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking_screen);

        readIntentExtras();
        if (sessionId == null) return; // guard already called finish()

        bindViews();
        setupBottomPanel();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        TrackingViewModelFactory factory = new TrackingViewModelFactory(getApplication());
        viewModel = new ViewModelProvider(this, factory).get(TrackingViewModel.class);
        viewModel.startTrackingSession(sessionId, partnerId, partnerName, meetingLat, meetingLng);
        viewModel.getUiState().observe(this, this::renderState);
        viewModel.getCompletionError().observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            }
        });

        setupMap();
        setupClickListeners();
    }

    /**
     * Stops the GPS service as a safety net when the user presses Back
     * without tapping Finish. {@code isFinishing()} guards against killing
     * GPS on a configuration change (screen rotation).
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            stopService(new Intent(this, WalkTrackerService.class));
            Log.d(TAG, "Safety-net: WalkTrackerService stopped on Activity finish");
        }
    }

    // ── Map ───────────────────────────────────────────────────────────────────

    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.mapContainer);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        applyMapBottomPaddingIfReady();

        // Disable default controls — we supply our own back button and re-center FAB.
        map.getUiSettings().setMyLocationButtonEnabled(false);
        map.getUiSettings().setCompassEnabled(false);
        map.getUiSettings().setZoomControlsEnabled(false);

        // Show the blue "you are here" dot if permission is already granted.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            //noinspection MissingPermission
            map.setMyLocationEnabled(true);
        } else {
            // Ask once on map load so recenter/initial zoom can work immediately.
            requestLocationPermission(false);
        }

        // Immediately center on the device location as the initial map position.
        // Don't move to meeting-point coordinates first.
        // Mark initial fly now so stale cached route points cannot override this
        // initial camera decision on map startup.
        zoomToDeviceLocation(true);

        // Re-apply the latest ViewModel state in case GPS points arrived before
        // the map finished loading (rare, but possible on slow devices).
        TrackingUiState latestState = viewModel.getUiState().getValue();
        if (latestState != null && !latestState.getMapPoints().isEmpty()) {
            updatePolyline(latestState.getMapPoints());
            if (latestState.isCameraFollowingUser()) {
                handleCameraUpdate(latestState);
            }
        } else if (latestState != null && latestState.getWalkState() == WalkState.ACTIVE) {
            // Walk already started but service hasn't delivered a point yet — zoom
            // immediately to the device's cached last-known location.
            zoomToDeviceLocation(true);
        }
    }

    // ── Bottom Panel ──────────────────────────────────────────────────────────

    private void setupBottomPanel() {
        bottomPanel.post(() -> {
            int panelHeight = bottomPanel.getHeight();
            if (panelHeight > 0) {
                bottomPanelHeightPx = panelHeight;
                applyMapBottomPaddingIfReady();
            }
        });
    }

    private void applyMapBottomPaddingIfReady() {
        if (googleMap != null && bottomPanelHeightPx > 0) {
            googleMap.setPadding(0, 0, 0, bottomPanelHeightPx);
        }
    }

    // ── Click listeners ───────────────────────────────────────────────────────

    private void setupClickListeners() {
        // Back button — same as pressing the system back key.
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Re-center — animate camera to the latest GPS point.
        fabRecenter.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                recenterCamera();
            } else {
                requestLocationPermission(false);
            }
        });

        // Start — requires ACCESS_FINE_LOCATION at runtime.
        btnStart.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                viewModel.startWalk();
                // Immediately fly to the cached last-known location so the user
                // doesn't stare at the meeting-point overview while waiting for
                // the first GPS fix from WalkTrackerService.
                zoomToDeviceLocation(true);
            } else {
                requestLocationPermission(true);
            }
        });

        // Pause / Resume — the button text reflects the current state;
        // the ViewModel ignores calls that are not valid in the current state.
        btnPause.setOnClickListener(v -> {
            TrackingUiState s = viewModel.getUiState().getValue();
            if (s == null) return;
            if (s.getWalkState() == WalkState.ACTIVE) {
                viewModel.pauseWalk();
            } else if (s.getWalkState() == WalkState.PAUSED) {
                viewModel.resumeWalk();
            }
        });

        // Verify Partner — opens QR screen.
        btnVerifyPartner.setOnClickListener(v -> {
            Intent qrIntent = new Intent(this, QrVerifyActivity.class);
            qrIntent.putExtra(QrVerifyActivity.EXTRA_SESSION_ID,     sessionId);
            qrIntent.putExtra(QrVerifyActivity.EXTRA_PARTNER_NAME,   partnerName);
            qrIntent.putExtra(QrVerifyActivity.EXTRA_PARTNER_AVATAR, partnerId);
            qrIntent.putExtra(QrVerifyActivity.EXTRA_HOTSPOT_NAME,   hotspotName != null ? hotspotName : "");
            startActivity(qrIntent);
        });

        // Complete Walk — confirmation dialog then API-backed completion.
        btnComplete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.complete_walk_confirm_title)
                    .setMessage(R.string.complete_walk_confirm_message)
                    .setPositiveButton(R.string.btn_complete_confirm,
                            (d, w) -> viewModel.requestCompleteWalk())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                            @NonNull String[] permissions,
                                            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Enable the blue dot now that we have the runtime grant.
            if (googleMap != null) {
                //noinspection MissingPermission
                googleMap.setMyLocationEnabled(true);
            }
            // Always center the map once permission is granted.
            zoomToDeviceLocation(false);

            // Only auto-start walk when this permission request came from Start.
            if (shouldStartWalkAfterPermission) {
                shouldStartWalkAfterPermission = false;
                viewModel.startWalk();
                zoomToDeviceLocation(true);
            }
        } else {
            shouldStartWalkAfterPermission = false;
            Toast.makeText(this,
                    R.string.tracking_permission_denied, Toast.LENGTH_LONG).show();
        }
    }

    private void requestLocationPermission(boolean startWalkAfterGrant) {
        shouldStartWalkAfterPermission = startWalkAfterGrant;
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQUEST_LOCATION_PERMISSION);
    }

    // ── State rendering ───────────────────────────────────────────────────────

    /**
     * The single method the Activity uses to react to ViewModel state.
     * Updates stats text, button visibility, map polyline, and camera.
     */
    private void renderState(TrackingUiState state) {
        updatePartnerHeader(state.getPartnerName());
        updateStats(state);
        updateControls(state);

        if (googleMap != null) {
            updatePolyline(state.getMapPoints());
            // Partner polyline — updated WITHOUT touching camera
            updatePartnerPolyline(state.getPartnerMapPoints());
            handleCameraUpdate(state);
            if (state.isCameraFollowingUser()
                    && state.getMapPoints().isEmpty()
                    && !hasInitialCameraFly) {
                zoomToDeviceLocation(true);
            }
        }
        updatePartnerStatusLabel(state.getPartnerOverlayState(),
                state.getPartnerLastUpdatedSeconds(),
                state.getWalkState());

        String notice = state.getPartnerNoticeMessage();
        if (notice != null) {
            Toast.makeText(this, notice, Toast.LENGTH_SHORT).show();
            viewModel.consumePartnerNotice();
        }

        if (state.getWalkState() == WalkState.FINISHED && !finishDialogShown) {
            finishDialogShown = true;
            showPostSessionSummary(state);
        }
    }

    private void updatePartnerHeader(String name) {
        txtPartnerNameSheet.setText(name);
        avatarPartner.bind(name, null);
    }

    private void updateStats(TrackingUiState state) {
        statDistance.setValue(formatDistance(state.getDistanceKm()));
        statDuration.setValue(formatDuration(state.getElapsedSeconds()));
        statPace.setValue(formatPace(state.getPaceMinPerKm()));
    }

    /**
     * Controls which button row is shown based on the walk state and new session lifecycle fields:
     * <ul>
     *   <li>{@code READY}     — Start only</li>
    *   <li>{@code ACTIVE}    — Pause/Resume row + Complete Walk</li>
     *   <li>{@code PAUSED}    — Pause/Resume row only</li>
    *   <li>{@code FINISHING} — Complete disabled (saving indicator)</li>
     *   <li>{@code FINISHED}  — all hidden (summary dialog takes over)</li>
     * </ul>
     */
    private void updateControls(TrackingUiState state) {
        WalkState walkState = state.getWalkState();

        // LIVE badge is only visible while actively walking.
        badgeLive.setVisibility(walkState == WalkState.ACTIVE ? View.VISIBLE : View.GONE);

        switch (walkState) {
            case READY:
                btnStart.setVisibility(View.VISIBLE);
                btnRowPauseStop.setVisibility(View.GONE);
                btnRowVerifyComplete.setVisibility(View.GONE);
                break;

            case ACTIVE:
                btnStart.setVisibility(View.GONE);
                btnRowPauseStop.setVisibility(View.VISIBLE);
                btnPause.setText(R.string.btn_pause);
                btnRowVerifyComplete.setVisibility(View.VISIBLE);
                long tooEarly = state.getCompleteTooEarlySeconds();
                if (tooEarly > 0) {
                    btnComplete.setEnabled(false);
                    btnComplete.setAlpha(0.6f);
                    btnComplete.setText(getString(R.string.tracking_complete_too_early_format, tooEarly));
                } else {
                    btnComplete.setEnabled(true);
                    btnComplete.setAlpha(1.0f);
                    btnComplete.setText(R.string.btn_complete_walk);
                }
                break;

            case PAUSED:
                btnStart.setVisibility(View.GONE);
                btnRowPauseStop.setVisibility(View.VISIBLE);
                btnPause.setText(R.string.btn_resume);
                btnRowVerifyComplete.setVisibility(View.VISIBLE);
                btnComplete.setEnabled(state.getCompleteTooEarlySeconds() == 0);
                if (state.getCompleteTooEarlySeconds() > 0) {
                    btnComplete.setAlpha(0.6f);
                    btnComplete.setText(getString(R.string.tracking_complete_too_early_format,
                            state.getCompleteTooEarlySeconds()));
                } else {
                    btnComplete.setAlpha(1.0f);
                    btnComplete.setText(R.string.btn_complete_walk);
                }
                break;

            case FINISHING:
                btnStart.setVisibility(View.GONE);
                btnRowPauseStop.setVisibility(View.GONE);
                btnRowVerifyComplete.setVisibility(View.VISIBLE);
                btnComplete.setEnabled(false);
                btnComplete.setAlpha(0.6f);
                btnComplete.setText(R.string.btn_complete_walk);
                break;

            case FINISHED:
                btnStart.setVisibility(View.GONE);
                btnRowPauseStop.setVisibility(View.GONE);
                btnRowVerifyComplete.setVisibility(View.GONE);
                break;
        }
    }

    // ── Map helpers ───────────────────────────────────────────────────────────

    /**
     * Updates the route polyline. Creates the {@link Polyline} on the first call
     * and reuses it via {@link Polyline#setPoints(List)} on subsequent calls —
     * avoids recreating the object every tick.
     */
    private void updatePolyline(List<LatLng> points) {
        if (points.isEmpty()) return;

        if (polyline == null) {
            polyline = googleMap.addPolyline(new PolylineOptions()
                    .color(POLYLINE_COLOR)
                    .width(10f)
                    .startCap(new RoundCap())
                    .endCap(new RoundCap())
                    .geodesic(true));
        }
        polyline.setPoints(points);
    }

    /**
     * Updates the partner polyline on the map.
     * Deliberately never touches the camera — partner updates must not
     * disrupt the user's own map view.
     */
    private void updatePartnerPolyline(List<LatLng> points) {
        if (googleMap == null || points.isEmpty()) return;

        if (partnerPolyline == null) {
            partnerPolyline = googleMap.addPolyline(new PolylineOptions()
                    .color(PARTNER_POLYLINE_COLOR)
                    .width(8f)
                    .startCap(new RoundCap())
                    .endCap(new RoundCap())
                    .geodesic(true));
        }
        partnerPolyline.setPoints(points); // setPoints only — no animateCamera
    }

    private void updatePartnerStatusLabel(PartnerOverlayState overlayState,
                                           long lastUpdatedSecs,
                                           WalkState currentUserState) {
        if (txtPartnerStatus == null) return;
        boolean userFinished = (currentUserState == WalkState.FINISHED
                || currentUserState == WalkState.FINISHING);

        switch (overlayState) {
            case WAITING_FOR_PARTNER:
                txtPartnerStatus.setVisibility(View.VISIBLE);
                txtPartnerStatus.setText(userFinished
                        ? R.string.tracking_self_done_partner_pending
                        : R.string.tracking_partner_waiting);
                break;
            case WAITING_FOR_GPS:
                // Partner is ACTIVE but hasn't sent GPS chunks yet — still show "Partner is walking".
                // Polyline absence is not a reason to hide active status from the user.
                txtPartnerStatus.setVisibility(View.VISIBLE);
                txtPartnerStatus.setText(R.string.tracking_partner_walking);
                break;
            case SHOWING_PATH:
                txtPartnerStatus.setVisibility(View.VISIBLE);
                if (lastUpdatedSecs >= 60) {
                    txtPartnerStatus.setText(
                            getString(R.string.tracking_partner_disconnected, lastUpdatedSecs));
                } else if (lastUpdatedSecs >= 15) {
                    txtPartnerStatus.setText(
                            getString(R.string.tracking_partner_last_updated, lastUpdatedSecs));
                } else {
                    // Fresh GPS (< 15 s) or self-done — partner is actively walking.
                    txtPartnerStatus.setText(R.string.tracking_partner_walking);
                }
                break;
            case PARTNER_COMPLETED:
                txtPartnerStatus.setVisibility(View.VISIBLE);
                txtPartnerStatus.setText(userFinished
                        ? R.string.tracking_both_finished
                        : R.string.tracking_partner_completed);
                break;
            case PARTNER_NO_SHOW:
                txtPartnerStatus.setVisibility(View.VISIBLE);
                txtPartnerStatus.setText(R.string.tracking_partner_no_show);
                break;
        }
    }

    /**
     * Flies the camera to the latest GPS point when:
     * <ul>
     *   <li>This is the very first point (initial fly-in regardless of follow state), or</li>
     *   <li>{@code isCameraFollowingUser} is true (walk is ACTIVE).</li>
     * </ul>
     * No animation on PAUSED/FINISHED so the user can freely pan the map.
     */
    private void handleCameraUpdate(TrackingUiState state) {
        List<LatLng> points = state.getMapPoints();
        if (points.isEmpty()) return;

        LatLng latest = points.get(points.size() - 1);

        if (!hasInitialCameraFly) {
            if (!state.isCameraFollowingUser()) {
                return;
            }
            hasInitialCameraFly = true;
            // First point: hard move (no animation) to avoid disorienting the user.
            googleMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(latest, MAP_TRACKING_ZOOM));
        } else if (state.isCameraFollowingUser()) {
            googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(latest, MAP_TRACKING_ZOOM));
        }
    }

    private void recenterCamera() {
        if (googleMap == null) return;
        TrackingUiState state = viewModel.getUiState().getValue();
        if (state != null && !state.getMapPoints().isEmpty()) {
            // Route has points — fly to the latest recorded position.
            LatLng latest = state.getMapPoints().get(state.getMapPoints().size() - 1);
            googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(latest, MAP_TRACKING_ZOOM));
        } else {
            // No route points yet (walk not started or waiting for first GPS fix)
            // — fall back to the device's cached last-known location.
            zoomToDeviceLocation(true);
        }
    }

    /**
     * Flies the camera to the device's current location.
     *
     * Strategy: try {@link FusedLocationProviderClient#getLastLocation()} first
     * (instant if a recent fix is cached). If it returns {@code null} — which
     * happens on a fresh boot, after a location-permission toggle, or when the
     * device hasn't moved in a long time — fall back to
     * {@link FusedLocationProviderClient#getCurrentLocation} to force a fresh fix.
     * This guarantees the camera always moves to the user rather than staying
     * pinned to the meeting-point coordinates.
     *
     * @param markInitialFly if {@code true}, sets {@link #hasInitialCameraFly} so that
     *                       the first route-point handler doesn't double-animate.
     *                       Pass {@code false} when calling before the walk has started
     *                       so the first real GPS fix can still trigger a zoom.
     */
    @SuppressWarnings("MissingPermission")
    private void zoomToDeviceLocation(boolean markInitialFly) {
        if (googleMap == null || fusedLocationClient == null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null && googleMap != null) {
                if (markInitialFly) hasInitialCameraFly = true;
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(location.getLatitude(), location.getLongitude()),
                        MAP_TRACKING_ZOOM));
            } else {
                // No cached fix — request a fresh location so the camera always
                // moves to the user instead of staying at the meeting point.
                fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener(this, freshLocation -> {
                            if (freshLocation != null && googleMap != null) {
                                if (markInitialFly) hasInitialCameraFly = true;
                                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                        new LatLng(freshLocation.getLatitude(),
                                                freshLocation.getLongitude()),
                                        MAP_TRACKING_ZOOM));
                            } else {
                                requestOneShotLocation(markInitialFly);
                            }
                        });
            }
        });
    }

    @SuppressWarnings("MissingPermission")
    private void requestOneShotLocation(boolean markInitialFly) {
        if (googleMap == null || fusedLocationClient == null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                .setMinUpdateIntervalMillis(500L)
                .setMaxUpdates(1)
                .build();

        LocationCallback callback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                android.location.Location loc = result.getLastLocation();
                if (loc != null && googleMap != null) {
                    if (markInitialFly) hasInitialCameraFly = true;
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(loc.getLatitude(), loc.getLongitude()),
                            MAP_TRACKING_ZOOM));
                }
                fusedLocationClient.removeLocationUpdates(this);
            }
        };

        fusedLocationClient.requestLocationUpdates(request, callback, getMainLooper());
    }

    // ── Walk completed → Post-Session Summary ─────────────────────────────────

    /**
     * Replaces the old summary dialog with a full PostSessionSummaryFragment.
     * The Fragment is added over android.R.id.content so it covers the tracking
     * UI without the user seeing the map underneath.
     */
    private void showPostSessionSummary(TrackingUiState state) {
        PostSessionSummaryFragment fragment =
                PostSessionSummaryFragment.newInstance(
                        sessionId, partnerName, partnerId,
                        state.getDistanceKm(), state.getElapsedSeconds());

        getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment, PostSessionSummaryFragment.TAG)
                .commit();
    }

    // ── Stat formatters ───────────────────────────────────────────────────────

    /** Returns {@code "1.23"} (2 decimal places). */
    static String formatDistance(double distanceKm) {
        return String.format(Locale.getDefault(), "%.2f", distanceKm);
    }

    /**
     * Returns {@code "MM:SS"} for walks under an hour, {@code "H:MM:SS"} beyond.
     * Examples: {@code "04:30"}, {@code "1:02:09"}.
     */
    static String formatDuration(long elapsedSeconds) {
        long h  = elapsedSeconds / 3600;
        long m  = (elapsedSeconds % 3600) / 60;
        long s  = elapsedSeconds % 60;
        if (h > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }

    /**
     * Returns {@code "--'--\""} until the 50 m threshold is crossed, then
     * {@code "X'YY\""} (e.g. {@code "6'45\""}).
     */
    static String formatPace(double paceMinPerKm) {
        if (paceMinPerKm <= 0.0) return "--'--\"";
        int min = (int) paceMinPerKm;
        int sec = (int) Math.round((paceMinPerKm - min) * 60.0);
        if (sec >= 60) { min++; sec = 0; }
        return String.format(Locale.getDefault(), "%d'%02d\"", min, sec);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void bindViews() {
        avatarPartner       = findViewById(R.id.avatarPartner);
        txtPartnerNameSheet = findViewById(R.id.txtPartnerNameSheet);
        txtPartnerStatus    = findViewById(R.id.txtPartnerStatus);
        statDistance        = findViewById(R.id.statDistance);
        statDuration        = findViewById(R.id.statDuration);
        statPace            = findViewById(R.id.statPace);
        btnStart            = findViewById(R.id.btnStart);
        btnRowPauseStop     = findViewById(R.id.btnRowPauseStop);
        btnPause            = findViewById(R.id.btnPause);
        btnRowVerifyComplete = findViewById(R.id.btnRowVerifyComplete);
        btnVerifyPartner     = findViewById(R.id.btnVerifyPartner);
        btnComplete          = findViewById(R.id.btnComplete);
        fabRecenter          = findViewById(R.id.fabRecenter);
        bottomPanel         = findViewById(R.id.bottomPanel);
        badgeLive           = findViewById(R.id.badgeLive);
    }

    /**
     * Reads all four Intent extras. Calls {@link #finish()} immediately if
     * {@code SESSION_ID} is absent — a tracking screen without a real session
     * is a programming error in the caller.
     */
    private void readIntentExtras() {
        Intent intent = getIntent();
        sessionId   = intent.getStringExtra(EXTRA_SESSION_ID);
        partnerId   = intent.getStringExtra(EXTRA_PARTNER_ID);
        partnerName = intent.getStringExtra(EXTRA_PARTNER_NAME);
        hotspotName = intent.getStringExtra(EXTRA_HOTSPOT_NAME);
        meetingLat  = intent.getDoubleExtra(EXTRA_MEETING_LAT, 0.0);
        meetingLng  = intent.getDoubleExtra(EXTRA_MEETING_LNG, 0.0);

        if (sessionId == null || sessionId.trim().isEmpty()) {
            Log.e(TAG, "Missing SESSION_ID — cannot open tracking screen.");
            finish();
            return;
        }

        Log.d(TAG, "TrackingScreen opened:"
                + "\n  sessionId   = " + sessionId
                + "\n  partnerId   = " + partnerId
                + "\n  partnerName = " + partnerName
                + "\n  meetingLat  = " + meetingLat
                + "\n  meetingLng  = " + meetingLng);
    }

}
