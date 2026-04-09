package com.walkmate.ui.history.routereplay;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;
import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.domain.walksession.SessionRoute;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Route Replay screen.
 *
 * Decodes the encoded polyline strings from {@link SessionRoute} and draws
 * two coloured paths on a Google Map:
 *   • User A — blue (Color.BLUE)
 *   • User B — red (Color.RED)
 *
 * Entry point: {@link com.walkmate.ui.history.SessionHistoryFragment} passes
 * {@code SESSION_ID} via Intent extra.
 */
public class RouteReplayActivity extends AppCompatActivity implements OnMapReadyCallback {

    public static final String EXTRA_SESSION_ID = "SESSION_ID";

    private static final String TAG         = "RouteReplayActivity";
    private static final int    PADDING_PX  = 120;   // map camera bounds padding

    private RouteReplayViewModel viewModel;
    private GoogleMap            googleMap;

    private ProgressBar progressBar;
    private TextView    txtError;
    private TextView    txtStats;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_replay);

        String sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        if (sessionId == null || sessionId.isEmpty()) {
            Log.e(TAG, "Missing SESSION_ID");
            finish();
            return;
        }

        progressBar = findViewById(R.id.progressRouteReplay);
        txtError    = findViewById(R.id.txtRouteReplayError);
        txtStats    = findViewById(R.id.txtRouteReplayStats);

        View btnBack = findViewById(R.id.btnBackRouteReplay);
        btnBack.setOnClickListener(v -> finish());

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.mapRouteReplay);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        WalkMateApplication app = (WalkMateApplication) getApplication();
        viewModel = new ViewModelProvider(this,
                new RouteReplayViewModelFactory(app.getWalkSessionRepository()))
                .get(RouteReplayViewModel.class);

        viewModel.getUiState().observe(this, this::renderState);
        viewModel.loadRoute(sessionId);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        map.getUiSettings().setMyLocationButtonEnabled(false);
        map.getUiSettings().setZoomControlsEnabled(true);

        // Draw any route that arrived before the map was ready.
        RouteReplayUiState latestState = viewModel.getUiState().getValue();
        if (latestState != null && latestState.kind == RouteReplayUiState.Kind.READY) {
            drawRoute(latestState.route);
        }
    }

    // ── State rendering ───────────────────────────────────────────────────────

    private void renderState(RouteReplayUiState state) {
        switch (state.kind) {
            case LOADING:
                progressBar.setVisibility(View.VISIBLE);
                txtError.setVisibility(View.GONE);
                break;

            case READY:
                progressBar.setVisibility(View.GONE);
                txtError.setVisibility(View.GONE);
                txtStats.setText(formatStats(state.route));
                txtStats.setVisibility(View.VISIBLE);
                if (googleMap != null) drawRoute(state.route);
                break;

            case ERROR:
                progressBar.setVisibility(View.GONE);
                txtError.setVisibility(View.VISIBLE);
                txtError.setText(state.error);
                break;
        }
    }

    // ── Map drawing ───────────────────────────────────────────────────────────

    /**
     * Decodes each polyline segment in the route and adds it to the map.
     * Uses {@link PolyUtil#decode(String)} for encoded strings.
     * Falls back to a single point if a segment cannot be decoded.
     *
     * User A paths → blue. User B paths → red.
     * Camera is moved to encompass all drawn points on completion.
     */
    private void drawRoute(SessionRoute route) {
        if (googleMap == null) return;
        googleMap.clear();

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        boolean hasPoints = false;

        hasPoints |= drawPolylines(route.getUserAPolylines(), Color.BLUE, boundsBuilder);
        hasPoints |= drawPolylines(route.getUserBPolylines(), Color.RED, boundsBuilder);

        if (hasPoints) {
            try {
                googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), PADDING_PX));
            } catch (IllegalStateException e) {
                // Bounds builder may throw if it has a single point — safe to ignore.
                Log.w(TAG, "LatLngBounds could not be built", e);
            }
        }
    }

    /**
     * Decodes and draws a list of encoded polyline strings.
     * Returns true if at least one point was added to the bounds builder.
     */
    private boolean drawPolylines(List<String> encodedSegments, int color,
                                   LatLngBounds.Builder boundsBuilder) {
        if (encodedSegments == null || encodedSegments.isEmpty()) return false;
        boolean hasPoints = false;

        for (String encoded : encodedSegments) {
            List<LatLng> points = decodePolyline(encoded);
            if (points.isEmpty()) continue;

            googleMap.addPolyline(new PolylineOptions()
                    .addAll(points)
                    .color(color)
                    .width(6f)
                    .geodesic(true));

            for (LatLng p : points) {
                boundsBuilder.include(p);
            }
            hasPoints = true;
        }
        return hasPoints;
    }

    /**
     * Decodes an encoded polyline string.
     * Returns an empty list on any error so the caller can safely skip the segment.
     */
    private static List<LatLng> decodePolyline(String encoded) {
        if (encoded == null || encoded.isEmpty()) return Collections.emptyList();
        try {
            return PolyUtil.decode(encoded);
        } catch (Exception e) {
            Log.w(TAG, "Failed to decode polyline segment", e);
            return Collections.emptyList();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String formatStats(SessionRoute route) {
        return String.format(Locale.getDefault(),
                "%.2f km · %d min",
                route.getTotalDistanceKm(),
                route.getDurationMinutes());
    }
}
