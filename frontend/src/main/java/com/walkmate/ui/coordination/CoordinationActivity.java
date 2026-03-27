package com.walkmate.ui.coordination;

import com.walkmate.R;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;

import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.ui.coordination.CoordinationUiState.AppState;
import com.walkmate.ui.coordination.createintent.CreateIntentBottomSheetFragment;
import com.walkmate.ui.coordination.matching.MatchingOverlayFragment;
import com.walkmate.ui.coordination.matchresult.MatchResultFragment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CoordinationActivity — pure UI host for the walk-coordination screen.
 *
 * Responsibilities:
 *   - Inflate views and set up the map fragment
 *   - Observe CoordinationViewModel.uiState and render accordingly
 *   - Forward user actions directly to ViewModel (no business logic here)
 *   - Manage fragment lifecycle (CreateIntent sheet, Matching overlay, Match result)
 */
public class CoordinationActivity extends AppCompatActivity
        implements OnMapReadyCallback,
        com.walkmate.ui.coordination.createintent.CreateIntentBottomSheetFragment.OnIntentActionListener,
        com.walkmate.ui.coordination.matching.MatchingOverlayFragment.OnMatchFoundListener,
        com.walkmate.ui.coordination.matchresult.MatchResultFragment.OnMatchResultActionListener {

    // ── Fragment tags ────────────────────────────────────────────────────
    private static final String TAG_MAP           = "map_fragment";
    private static final String TAG_INTENT_SHEET  = "create_intent";
    private static final String TAG_MATCHING      = "matching_overlay";
    private static final String TAG_MATCH_RESULT  = "match_result";

    // ── Views ────────────────────────────────────────────────────────────
    private View dimOverlay;
    private LinearLayout hotspotCtaCard;
    private TextView txtHotspotName;
    private MaterialButton btnSetIntent;

    // ── Map ──────────────────────────────────────────────────────────────
    private GoogleMap googleMap;
    private final Map<String, Marker> markerByHotspotId = new HashMap<>();
    private final Map<String, Hotspot> hotspotById      = new HashMap<>();

    // ── ViewModel ────────────────────────────────────────────────────────
    private CoordinationViewModel viewModel;

    // ════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_coordination);

        bindViews();
        setupViewModel();
        setupMap();
        setupListeners();
        setupBackPressHandling();
    }

    // ════════════════════════════════════════════════════════════════════
    // VIEW BINDING
    // ════════════════════════════════════════════════════════════════════

    private void bindViews() {
        dimOverlay     = findViewById(R.id.dimOverlay);
        hotspotCtaCard = findViewById(R.id.hotspotCtaCard);
        txtHotspotName = findViewById(R.id.txtHotspotName);
        btnSetIntent   = findViewById(R.id.btnSetIntent);
    }

    // ════════════════════════════════════════════════════════════════════
    // VIEWMODEL — observe and render
    // ════════════════════════════════════════════════════════════════════

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this, new CoordinationViewModelFactory())
                .get(CoordinationViewModel.class);

        viewModel.getUiState().observe(this, this::renderState);
    }

    // ════════════════════════════════════════════════════════════════════
    // MAP SETUP
    // ════════════════════════════════════════════════════════════════════

    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentByTag(TAG_MAP);
        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.mapContainer, mapFragment, TAG_MAP)
                    .commitNow();
        }
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setCompassEnabled(false);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);

        googleMap.setOnMapClickListener(latLng -> {
            CoordinationUiState state = viewModel.getUiState().getValue();
            if (state != null && state.getAppState() == AppState.CREATE_INTENT) {
                viewModel.closeCreateIntent();
            }
        });

        googleMap.setOnMarkerClickListener(marker -> {
            if (!(marker.getTag() instanceof String)) return false;
            viewModel.selectHotspot((String) marker.getTag());
            return true;
        });

        // Hotspots may already be loaded before the map was ready
        CoordinationUiState current = viewModel.getUiState().getValue();
        if (current != null && !current.getHotspots().isEmpty()) {
            drawHotspotMarkers(current.getHotspots());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // STATE RENDERING — single point of truth for all UI updates
    // ════════════════════════════════════════════════════════════════════

    private void renderState(CoordinationUiState state) {
        // Draw markers once when hotspots arrive and map is ready
        if (googleMap != null && markerByHotspotId.isEmpty() && !state.getHotspots().isEmpty()) {
            drawHotspotMarkers(state.getHotspots());
        }

        // Sync marker selection highlight
        if (googleMap != null) {
            Hotspot sel = state.getSelectedHotspot();
            updateMarkerSelection(sel != null ? sel.getId() : null);
        }

        // Sync UI per AppState
        switch (state.getAppState()) {
            case IDLE:
                hideWithAnim(hotspotCtaCard, R.anim.fade_out);
                animateDimOverlay(false);
                dismissFragmentByTag(TAG_INTENT_SHEET);
                dismissFragmentByTag(TAG_MATCHING);
                dismissFragmentByTag(TAG_MATCH_RESULT);
                break;

            case HOTSPOT_SELECTED:
                animateDimOverlay(false);
                dismissFragmentByTag(TAG_INTENT_SHEET);
                dismissFragmentByTag(TAG_MATCHING);
                dismissFragmentByTag(TAG_MATCH_RESULT);
                if (state.getSelectedHotspot() != null) {
                    txtHotspotName.setText(state.getSelectedHotspot().getName());
                    zoomToHotspot(state.getSelectedHotspot());
                }
                showWithAnim(hotspotCtaCard, R.anim.slide_up);
                break;

            case CREATE_INTENT:
                hideWithAnim(hotspotCtaCard, R.anim.fade_out);
                animateDimOverlay(true);
                ensureCreateIntentSheet();
                break;

            case MATCHING:
                hideView(hotspotCtaCard);
                animateDimOverlay(true);
                dismissFragmentByTag(TAG_INTENT_SHEET);
                ensureMatchingOverlay(state);
                break;

            case MATCH_RESULT:
                hideView(hotspotCtaCard);
                animateDimOverlay(true);
                dismissFragmentByTag(TAG_MATCHING);
                ensureMatchResultDialog();
                break;
        }

        // One-shot error toast
        if (state.getError() != null) {
            Toast.makeText(this, state.getError(), Toast.LENGTH_SHORT).show();
            viewModel.consumeError();
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // MAP HELPERS
    // ════════════════════════════════════════════════════════════════════

    private void drawHotspotMarkers(List<Hotspot> hotspots) {
        googleMap.clear();
        hotspotById.clear();
        markerByHotspotId.clear();

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (Hotspot hotspot : hotspots) {
            hotspotById.put(hotspot.getId(), hotspot);
            LatLng position = new LatLng(hotspot.getLat(), hotspot.getLng());
            boundsBuilder.include(position);

            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(position)
                    .title(hotspot.getName())
                    .anchor(0.5f, 1f)
                    .icon(createMarkerIcon(hotspot.getName(), false)));

            if (marker != null) {
                marker.setTag(hotspot.getId());
                markerByHotspotId.put(hotspot.getId(), marker);
            }
        }

        if (!hotspots.isEmpty()) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 180));
        }
    }

    private void zoomToHotspot(Hotspot hotspot) {
        if (googleMap == null) return;
        LatLng target = new LatLng(hotspot.getLat(), hotspot.getLng());
        googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(target, 15f),
                400,
                null);
    }

    private void updateMarkerSelection(String selectedId) {
        for (Map.Entry<String, Marker> entry : markerByHotspotId.entrySet()) {
            boolean isSelected = entry.getKey().equals(selectedId);
            Hotspot hotspot = hotspotById.get(entry.getKey());
            if (hotspot == null) continue;
            Marker marker = entry.getValue();
            marker.setIcon(createMarkerIcon(hotspot.getName(), isSelected));
            marker.setZIndex(isSelected ? 20f : 10f);
        }
    }

    private com.google.android.gms.maps.model.BitmapDescriptor createMarkerIcon(
            String labelText, boolean isSelected) {
        View markerView = LayoutInflater.from(this)
                .inflate(R.layout.item_map_marker, null, false);
        View pill     = markerView.findViewById(R.id.markerPill);
        TextView label = markerView.findViewById(R.id.markerLabel);
        View tail     = markerView.findViewById(R.id.markerTail);

        label.setText(labelText);
        pill.setBackgroundResource(isSelected
                ? R.drawable.bg_marker_selected : R.drawable.bg_marker_unselected);
        label.setTextColor(isSelected ? Color.WHITE : Color.parseColor("#332218"));

        GradientDrawable tailBg = new GradientDrawable();
        tailBg.setShape(GradientDrawable.RECTANGLE);
        tailBg.setColor(isSelected ? Color.parseColor("#FF7B3A") : Color.WHITE);
        tail.setBackground(tailBg);

        int widthSpec = View.MeasureSpec.makeMeasureSpec(
                getResources().getDisplayMetrics().widthPixels, View.MeasureSpec.AT_MOST);
        markerView.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        int w = markerView.getMeasuredWidth();
        int h = markerView.getMeasuredHeight();
        if (w <= 0 || h <= 0) {
            return BitmapDescriptorFactory.defaultMarker(
                    isSelected ? BitmapDescriptorFactory.HUE_ORANGE : BitmapDescriptorFactory.HUE_RED);
        }

        markerView.layout(0, 0, w, h);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        markerView.draw(new Canvas(bitmap));
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    // ════════════════════════════════════════════════════════════════════
    // LISTENERS
    // ════════════════════════════════════════════════════════════════════

    private void setupListeners() {
        btnSetIntent.setOnClickListener(v -> viewModel.openCreateIntent());

        dimOverlay.setClickable(true);
        dimOverlay.setFocusable(true);
        dimOverlay.setOnClickListener(v -> {
            CoordinationUiState state = viewModel.getUiState().getValue();
            if (state == null) return;
            if (state.getAppState() == AppState.CREATE_INTENT) {
                viewModel.closeCreateIntent();
            } else if (state.getAppState() == AppState.MATCH_RESULT) {
                viewModel.resetToIdle();
            }
        });
    }

    // ════════════════════════════════════════════════════════════════════
    // FRAGMENT CALLBACKS
    // ════════════════════════════════════════════════════════════════════

    @Override
    public void onIntentCreated(WalkIntent intent) {
        viewModel.onIntentCreated(intent);
    }

    @Override
    public void onSheetDismissed() {
        // Only act if we're still in CREATE_INTENT (not dismissed programmatically)
        CoordinationUiState state = viewModel.getUiState().getValue();
        if (state != null && state.getAppState() == AppState.CREATE_INTENT) {
            viewModel.closeCreateIntent();
        }
    }

    @Override
    public void onMatchTimerComplete() {
        dismissFragmentByTag(TAG_MATCHING);
        viewModel.onMatchTimerComplete();
    }

    @Override
    public void onAcceptClicked() {
        viewModel.resetToIdle();
    }

    @Override
    public void onPassClicked() {
        viewModel.resetToIdle();
    }

    // ════════════════════════════════════════════════════════════════════
    // FRAGMENT MANAGEMENT
    // ════════════════════════════════════════════════════════════════════

    private void ensureCreateIntentSheet() {
        if (getSupportFragmentManager().findFragmentByTag(TAG_INTENT_SHEET) != null) return;
        CoordinationUiState state = viewModel.getUiState().getValue();
        String hotspotId = (state != null && state.getSelectedHotspot() != null)
                ? state.getSelectedHotspot().getId() : "";
        CreateIntentBottomSheetFragment fragment =
                CreateIntentBottomSheetFragment.newInstance(hotspotId);
        fragment.setOnIntentActionListener(this);
        fragment.show(getSupportFragmentManager(), TAG_INTENT_SHEET);
    }

    private void ensureMatchingOverlay(CoordinationUiState state) {
        if (getSupportFragmentManager().findFragmentByTag(TAG_MATCHING) != null) return;
        String name = state.getSelectedHotspot() != null ? state.getSelectedHotspot().getName() : "";
        MatchingOverlayFragment fragment = MatchingOverlayFragment.newInstance(name);
        fragment.setOnMatchFoundListener(this);
        fragment.show(getSupportFragmentManager(), TAG_MATCHING);
    }

    private void ensureMatchResultDialog() {
        if (getSupportFragmentManager().findFragmentByTag(TAG_MATCH_RESULT) != null) return;
        MatchResultFragment fragment = new MatchResultFragment();
        fragment.setOnMatchResultActionListener(this);
        fragment.show(getSupportFragmentManager(), TAG_MATCH_RESULT);
    }

    private void dismissFragmentByTag(String tag) {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if (fragment instanceof androidx.fragment.app.DialogFragment) {
            ((androidx.fragment.app.DialogFragment) fragment).dismissAllowingStateLoss();
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // ANIMATION HELPERS
    // ════════════════════════════════════════════════════════════════════

    private void showWithAnim(View view, int animResId) {
        if (view.getVisibility() == View.VISIBLE) return;
        view.setVisibility(View.VISIBLE);
        view.startAnimation(AnimationUtils.loadAnimation(this, animResId));
    }

    private void hideWithAnim(View view, int animResId) {
        if (view.getVisibility() != View.VISIBLE) return;
        Animation anim = AnimationUtils.loadAnimation(this, animResId);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation a) {}
            @Override public void onAnimationRepeat(Animation a) {}
            @Override public void onAnimationEnd(Animation a) { view.setVisibility(View.GONE); }
        });
        view.startAnimation(anim);
    }

    private void hideView(View view) {
        view.clearAnimation();
        view.setVisibility(View.GONE);
    }

    private void animateDimOverlay(boolean show) {
        if (show) {
            dimOverlay.setVisibility(View.VISIBLE);
            dimOverlay.setAlpha(0f);
            dimOverlay.animate().alpha(1f).setDuration(300).start();
        } else {
            dimOverlay.animate().alpha(0f).setDuration(250)
                    .withEndAction(() -> dimOverlay.setVisibility(View.GONE))
                    .start();
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // BACK PRESS
    // ════════════════════════════════════════════════════════════════════

    private void setupBackPressHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!handleBackPressed()) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    private boolean handleBackPressed() {
        CoordinationUiState state = viewModel.getUiState().getValue();
        if (state == null) return false;
        switch (state.getAppState()) {
            case MATCH_RESULT:
            case HOTSPOT_SELECTED:
                viewModel.resetToIdle();
                return true;
            case MATCHING:
                return true; // cannot cancel during matching
            case CREATE_INTENT:
                viewModel.closeCreateIntent();
                return true;
            default:
                return false;
        }
    }
}
