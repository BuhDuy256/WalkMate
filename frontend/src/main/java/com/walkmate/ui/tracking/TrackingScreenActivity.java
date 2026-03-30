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
import com.walkmate.domain.tracking.WalkState;
import com.walkmate.service.WalkTrackerService;

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
    public static final String EXTRA_PARTNER_NAME = WalkTrackerService.EXTRA_PARTNER_NAME;
    public static final String EXTRA_MEETING_LAT  = WalkTrackerService.EXTRA_MEETING_LAT;
    public static final String EXTRA_MEETING_LNG  = WalkTrackerService.EXTRA_MEETING_LNG;

    private static final String TAG = "TrackingScreenActivity";
    private static final int    REQUEST_LOCATION_PERMISSION = 100;
    private static final float  MAP_DEFAULT_ZOOM   = 16.5f; // meeting-point overview
    private static final float  MAP_TRACKING_ZOOM  = 18.5f; // close follow during walk
    private static final int    POLYLINE_COLOR      = 0xFFFF7B3A; // orange_end

    // ── Session contract ──────────────────────────────────────────────────────

    private String sessionId;
    private String partnerName;
    private double meetingLat;
    private double meetingLng;

    // ── ViewModel ─────────────────────────────────────────────────────────────

    private TrackingViewModel viewModel;

    // ── Map ───────────────────────────────────────────────────────────────────

    private GoogleMap    googleMap;
    private Polyline     polyline;
    /** True after the camera has flown to the first GPS point. */
    private boolean      hasInitialCameraFly = false;

    // ── Views ─────────────────────────────────────────────────────────────────

    private TextView              txtPartnerInitial;
    private TextView              txtPartnerNameSheet;
    private TextView              txtDistance;
    private TextView              txtDuration;
    private TextView              txtPace;
    private MaterialButton        btnStart;
    private LinearLayout          btnRowPauseStop;
    private MaterialButton        btnPause;
    private MaterialButton        btnStop;
    private FloatingActionButton  fabRecenter;
    private LinearLayout          bottomPanel;

    // ── Flags ─────────────────────────────────────────────────────────────────

    private boolean finishDialogShown = false;
    private int     bottomPanelHeightPx = 0;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking_screen);

        readIntentExtras();
        if (sessionId == null) return; // guard already called finish()

        bindViews();
        setupBottomPanel();
        setupMap();
        setupClickListeners();

        TrackingViewModelFactory factory = new TrackingViewModelFactory(getApplication());
        viewModel = new ViewModelProvider(this, factory).get(TrackingViewModel.class);
        viewModel.startTrackingSession(sessionId, partnerName, meetingLat, meetingLng);
        viewModel.getUiState().observe(this, this::renderState);
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

        // Fly to meeting point as the initial map position.
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(meetingLat, meetingLng), MAP_DEFAULT_ZOOM));

        // Re-apply the latest ViewModel state in case GPS points arrived before
        // the map finished loading (rare, but possible on slow devices).
        TrackingUiState latestState = viewModel.getUiState().getValue();
        if (latestState != null && !latestState.getMapPoints().isEmpty()) {
            updatePolyline(latestState.getMapPoints());
            handleCameraUpdate(latestState);
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
        fabRecenter.setOnClickListener(v -> recenterCamera());

        // Start — requires ACCESS_FINE_LOCATION at runtime.
        btnStart.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                viewModel.startWalk();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_LOCATION_PERMISSION);
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

        // Finish — ViewModel stops GPS + transitions to FINISHED; we then close.
        btnStop.setOnClickListener(v -> {
            viewModel.finishWalk();
            // renderState() will react to FINISHED and show the summary dialog.
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
            viewModel.startWalk();
        } else {
            Toast.makeText(this,
                    R.string.tracking_permission_denied, Toast.LENGTH_LONG).show();
        }
    }

    // ── State rendering ───────────────────────────────────────────────────────

    /**
     * The single method the Activity uses to react to ViewModel state.
     * Updates stats text, button visibility, map polyline, and camera.
     */
    private void renderState(TrackingUiState state) {
        updatePartnerHeader(state.getPartnerName());
        updateStats(state);
        updateControls(state.getWalkState());

        if (googleMap != null) {
            updatePolyline(state.getMapPoints());
            handleCameraUpdate(state);
        }

        if (state.getWalkState() == WalkState.FINISHED && !finishDialogShown) {
            finishDialogShown = true;
            showWalkCompletedDialog(state);
        }
    }

    private void updatePartnerHeader(String name) {
        txtPartnerNameSheet.setText(name);
        if (name != null && !name.isEmpty()) {
            txtPartnerInitial.setText(
                    String.valueOf(name.charAt(0)).toUpperCase(Locale.getDefault()));
        }
    }

    private void updateStats(TrackingUiState state) {
        txtDistance.setText(formatDistance(state.getDistanceKm()));
        txtDuration.setText(formatDuration(state.getElapsedSeconds()));
        txtPace.setText(formatPace(state.getPaceMinPerKm()));
    }

    /**
     * Controls which button row is shown based on the walk state:
     * <ul>
     *   <li>{@code READY}    — Start only</li>
     *   <li>{@code ACTIVE}   — Pause ("Pause") + Stop</li>
     *   <li>{@code PAUSED}   — Pause ("Resume") + Stop</li>
     *   <li>{@code FINISHED} — all hidden (summary dialog takes over)</li>
     * </ul>
     */
    private void updateControls(WalkState state) {
        switch (state) {
            case READY:
                btnStart.setVisibility(View.VISIBLE);
                btnRowPauseStop.setVisibility(View.GONE);
                break;

            case ACTIVE:
                btnStart.setVisibility(View.GONE);
                btnRowPauseStop.setVisibility(View.VISIBLE);
                btnPause.setText(R.string.btn_pause);
                break;

            case PAUSED:
                btnStart.setVisibility(View.GONE);
                btnRowPauseStop.setVisibility(View.VISIBLE);
                btnPause.setText(R.string.btn_resume);
                break;

            case FINISHED:
                btnStart.setVisibility(View.GONE);
                btnRowPauseStop.setVisibility(View.GONE);
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
        if (state == null || state.getMapPoints().isEmpty()) return;

        LatLng latest = state.getMapPoints().get(state.getMapPoints().size() - 1);
        googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(latest, MAP_TRACKING_ZOOM));
    }

    // ── Walk completed dialog ─────────────────────────────────────────────────

    private void showWalkCompletedDialog(TrackingUiState state) {
        String message = getString(R.string.walk_completed_message,
                formatDistance(state.getDistanceKm()),
                formatDuration(state.getElapsedSeconds()),
                formatPace(state.getPaceMinPerKm()));

        new AlertDialog.Builder(this)
                .setTitle(R.string.walk_completed_title)
                .setMessage(message)
                .setPositiveButton(R.string.btn_done, (d, w) -> finish())
                .setCancelable(false)
                .show();
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
        txtPartnerInitial   = findViewById(R.id.txtPartnerInitial);
        txtPartnerNameSheet = findViewById(R.id.txtPartnerNameSheet);
        txtDistance         = findViewById(R.id.txtDistance);
        txtDuration         = findViewById(R.id.txtDuration);
        txtPace             = findViewById(R.id.txtPace);
        btnStart            = findViewById(R.id.btnStart);
        btnRowPauseStop     = findViewById(R.id.btnRowPauseStop);
        btnPause            = findViewById(R.id.btnPause);
        btnStop             = findViewById(R.id.btnStop);
        fabRecenter         = findViewById(R.id.fabRecenter);
        bottomPanel         = findViewById(R.id.bottomPanel);
    }

    /**
     * Reads all four Intent extras. Calls {@link #finish()} immediately if
     * {@code SESSION_ID} is absent — a tracking screen without a real session
     * is a programming error in the caller.
     */
    private void readIntentExtras() {
        Intent intent = getIntent();
        sessionId   = intent.getStringExtra(EXTRA_SESSION_ID);
        partnerName = intent.getStringExtra(EXTRA_PARTNER_NAME);
        meetingLat  = intent.getDoubleExtra(EXTRA_MEETING_LAT, 0.0);
        meetingLng  = intent.getDoubleExtra(EXTRA_MEETING_LNG, 0.0);

        if (sessionId == null || sessionId.trim().isEmpty()) {
            Log.e(TAG, "Missing SESSION_ID — cannot open tracking screen.");
            finish();
            return;
        }

        Log.d(TAG, "TrackingScreen opened:"
                + "\n  sessionId   = " + sessionId
                + "\n  partnerName = " + partnerName
                + "\n  meetingLat  = " + meetingLat
                + "\n  meetingLng  = " + meetingLng);
    }

}
