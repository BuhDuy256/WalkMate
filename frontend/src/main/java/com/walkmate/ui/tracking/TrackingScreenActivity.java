package com.walkmate.ui.tracking;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.walkmate.R;
import com.walkmate.service.WalkTrackerService;

/**
 * Entry-point for the GPS walk tracking screen.
 *
 * ── Phase 2 scope ─────────────────────────────────────────────────────────────
 * This is an intentional skeleton. It validates and stores the session contract
 * from the Intent, wires the service stop-on-exit behaviour, and provides the
 * public {@link #finishWalk()} hook that Phase 4/5 will expand with ViewModel
 * commands and the summary UI.
 *
 * ── PHASE 2 FIX: Intent extras replace hardcoded session ID ──────────────────
 * All four pieces of data that previously lived as hardcoded constants in the
 * old architecture doc are now read from the launch Intent:
 *   • {@code SESSION_ID}        — fed to WalkTrackerService and TrackingViewModel
 *   • {@code PARTNER_NAME}      — shown in the stats sheet header
 *   • {@code MEETING_POINT_LAT} — initial camera position for the map
 *   • {@code MEETING_POINT_LNG} — initial camera position for the map
 *
 * ── PHASE 2 FIX: Service stop on Activity exit ───────────────────────────────
 * Both {@link #finishWalk()} and {@link #onDestroy()} stop {@link WalkTrackerService}
 * to ensure the foreground service is never orphaned when the user leaves this
 * screen.
 *
 * {@link #onDestroy()} guards with {@link #isFinishing()} so a configuration
 * change (e.g. screen rotation) does NOT kill the GPS service mid-walk.
 *
 * ── TODO Phase 3 ─────────────────────────────────────────────────────────────
 * Wire {@code SessionAdapter.OnStartWalkClickListener} in SessionFragment to
 * launch this Activity with the correct extras.
 *
 * ── TODO Phase 4 ─────────────────────────────────────────────────────────────
 * Add TrackingViewModel + MediatorLiveData; wire Start / Pause / Resume buttons.
 *
 * ── TODO Phase 5 ─────────────────────────────────────────────────────────────
 * Replace the placeholder layout with the full CoordinatorLayout + stats sheet.
 */
public class TrackingScreenActivity extends AppCompatActivity {

    // ── Intent extra keys (must match WalkTrackerService) ─────────────────────

    public static final String EXTRA_SESSION_ID   = WalkTrackerService.EXTRA_SESSION_ID;
    public static final String EXTRA_PARTNER_NAME = WalkTrackerService.EXTRA_PARTNER_NAME;
    public static final String EXTRA_MEETING_LAT  = WalkTrackerService.EXTRA_MEETING_LAT;
    public static final String EXTRA_MEETING_LNG  = WalkTrackerService.EXTRA_MEETING_LNG;

    private static final String TAG = "TrackingScreenActivity";

    // ── Session contract (read from Intent; never hardcoded) ──────────────────

    private String sessionId;
    private String partnerName;
    private double meetingLat;
    private double meetingLng;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking_screen);

        readAndLogIntentExtras();

        // Phase 4: viewModel = new ViewModelProvider(this, factory).get(TrackingViewModel.class);
        // Phase 4: viewModel.init(sessionId, partnerName, meetingLat, meetingLng);
        // Phase 4: viewModel.getUiState().observe(this, this::renderState);

        // Phase 5: set up MapFragment, bottom sheet, control buttons here.
    }

    /**
     * ── PHASE 2 FIX: Guard with isFinishing() ────────────────────────────────
     * Only stops the service when the Activity is truly closing (back-press,
     * {@link #finishWalk()}, or OS kill). A configuration change such as
     * screen rotation sets {@code isFinishing() == false}, so the GPS service
     * survives undisturbed.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            stopService(new Intent(this, WalkTrackerService.class));
            Log.d(TAG, "WalkTrackerService stopped — Activity finishing");
        }
    }

    // ── Walk lifecycle commands (expanded in Phase 4) ─────────────────────────

    /**
     * Called by the Stop / Finish button.
     *
     * <p>Stops the GPS service explicitly before finishing the Activity so
     * the foreground notification is removed immediately rather than waiting
     * for {@link #onDestroy()} to run.
     *
     * <p>Phase 4 will call {@code viewModel.finishWalk()} here first to
     * transition state to {@code FINISHED} and persist the final summary.
     */
    public void finishWalk() {
        // Phase 4: viewModel.finishWalk();
        stopService(new Intent(this, WalkTrackerService.class));
        Log.d(TAG, "Walk finished — service stopped, session=" + sessionId);
        finish();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * ── PHASE 2 FIX: Reads all four Intent extras; logs them for diagnostics.
     * If SESSION_ID is missing the Activity finishes immediately — starting a
     * tracking screen without a real session is a programming error.
     */
    private void readAndLogIntentExtras() {
        Intent intent = getIntent();

        sessionId   = intent.getStringExtra(EXTRA_SESSION_ID);
        partnerName = intent.getStringExtra(EXTRA_PARTNER_NAME);
        meetingLat  = intent.getDoubleExtra(EXTRA_MEETING_LAT, 0.0);
        meetingLng  = intent.getDoubleExtra(EXTRA_MEETING_LNG, 0.0);

        if (sessionId == null || sessionId.trim().isEmpty()) {
            Log.e(TAG, "No SESSION_ID in Intent — cannot open tracking screen.");
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
