package com.walkmate.ui.explore;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.RangeSlider;
import com.walkmate.R;
import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.ui.explore.ExploreUiState.AppState;
import com.walkmate.ui.explore.createintent.CreateIntentUiState;
import com.walkmate.ui.explore.createintent.CreateIntentViewModel;
import com.walkmate.ui.explore.createintent.CreateIntentViewModelFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tab 1: Explore.
 *
 * State machine:
 *   WELCOME  → Welcome bottom sheet + hotspot chips
 *   SETUP    → Embedded Create Intent form, sheet EXPANDED, camera zoomed to hotspot
 *   SCANNING → Scanning bottom sheet + PulseOverlayView on map, markers locked
 *
 * Phase B6 (Scanning floating card / pulse) and B7 (cancel flow) are complete.
 */
public class ExploreFragment extends Fragment implements OnMapReadyCallback {

    public static final String TAG = "ExploreFragment";

    private static final String TAG_MAP           = "explore_map_fragment";
    private static final int    MAX_HOTSPOT_CHIPS = 5;

    // ── Views ────────────────────────────────────────────────────────────
    private View       dimOverlay;
    private FrameLayout mapContainer;      // needed to host PulseOverlayView

    // Bottom sheet ────────────────────────────────────────────────────────
    private View                    bottomSheet;
    private BottomSheetBehavior<View> sheetBehavior;
    private View                    welcomeContent;
    private View                    setupContent;
    private View                    scanningContent;

    // Welcome content ─────────────────────────────────────────────────────
    private ChipGroup chipGroupHotspots;

    // Setup (Create Intent) form ──────────────────────────────────────────
    private TextView       txtSetupHotspotName;
    private RangeSlider    sliderTime;
    private RangeSlider    sliderAge;
    private TextView       txtTimeStart;
    private TextView       txtTimeEnd;
    private TextView       txtAgeMin;
    private TextView       txtAgeMax;
    private MaterialButton btnFindMatch;

    // Scanning sheet ──────────────────────────────────────────────────────
    private TextView       txtScanningHotspotName;
    private MaterialButton btnStopSearching;

    // ── Map ──────────────────────────────────────────────────────────────
    private GoogleMap googleMap;
    private final Map<String, Marker>  markerByHotspotId = new HashMap<>();
    private final Map<String, Hotspot> hotspotById       = new HashMap<>();

    // ── Pulse animation ──────────────────────────────────────────────────
    private PulseOverlayView pulseOverlay;

    // ── ViewModels ───────────────────────────────────────────────────────
    private ExploreViewModel      viewModel;
    private CreateIntentViewModel createIntentViewModel;

    // ════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════════════════

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_explore, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupViewModel();
        setupMap();
        setupBottomSheet();
        setupCreateIntentListeners();
        setupListeners();
        setupBackPressHandling();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Cancel the ValueAnimator before the view is detached to prevent leaks.
        stopPulseAnimation();
    }

    // ════════════════════════════════════════════════════════════════════
    // VIEW BINDING
    // ════════════════════════════════════════════════════════════════════

    private void bindViews(View root) {
        dimOverlay             = root.findViewById(R.id.dimOverlay);
        mapContainer           = root.findViewById(R.id.mapContainer);

        bottomSheet            = root.findViewById(R.id.bottomSheet);
        welcomeContent         = root.findViewById(R.id.welcomeContent);
        setupContent           = root.findViewById(R.id.setupContent);
        scanningContent        = root.findViewById(R.id.scanningContent);

        chipGroupHotspots      = root.findViewById(R.id.chipGroupHotspots);

        txtSetupHotspotName    = root.findViewById(R.id.txtSetupHotspotName);
        sliderTime             = root.findViewById(R.id.sliderTime);
        sliderAge              = root.findViewById(R.id.sliderAge);
        txtTimeStart           = root.findViewById(R.id.txtTimeStart);
        txtTimeEnd             = root.findViewById(R.id.txtTimeEnd);
        txtAgeMin              = root.findViewById(R.id.txtAgeMin);
        txtAgeMax              = root.findViewById(R.id.txtAgeMax);
        btnFindMatch           = root.findViewById(R.id.btnFindMatch);

        txtScanningHotspotName = root.findViewById(R.id.txtScanningHotspotName);
        btnStopSearching       = root.findViewById(R.id.btnStopSearching);
    }

    // ════════════════════════════════════════════════════════════════════
    // VIEWMODEL — setup and observation
    // ════════════════════════════════════════════════════════════════════

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this, new ExploreViewModelFactory(requireContext()))
                .get(ExploreViewModel.class);
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);

        createIntentViewModel = new ViewModelProvider(
                this, new CreateIntentViewModelFactory(requireContext()))
                .get(CreateIntentViewModel.class);
        createIntentViewModel.getUiState().observe(
                getViewLifecycleOwner(), this::renderCreateIntentState);
    }

    // ════════════════════════════════════════════════════════════════════
    // MAP SETUP — uses getChildFragmentManager() (critical for Fragments)
    // ════════════════════════════════════════════════════════════════════

    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentByTag(TAG_MAP);
        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance();
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.mapContainer, mapFragment, TAG_MAP)
                    .commitNow();
        }
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setCompassEnabled(false);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);

        googleMap.setOnMapClickListener(latLng -> {
            ExploreUiState state = viewModel.getUiState().getValue();
            if (state == null || state.getAppState() == AppState.SCANNING) return;
            if (state.getAppState() == AppState.SETUP) {
                viewModel.closeSetup();
            }
        });

        googleMap.setOnMarkerClickListener(marker -> {
            // Block interaction while a scan is in progress.
            ExploreUiState state = viewModel.getUiState().getValue();
            if (state != null && state.getAppState() == AppState.SCANNING) return true;

            if (!(marker.getTag() instanceof String)) return false;
            viewModel.selectHotspot((String) marker.getTag());
            return true;
        });

        // Hotspots may have loaded before the map was ready; draw them now.
        ExploreUiState current = viewModel.getUiState().getValue();
        if (current != null && !current.getHotspots().isEmpty()) {
            drawHotspotMarkers(current.getHotspots());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // BOTTOM SHEET SETUP
    // ════════════════════════════════════════════════════════════════════

    private void setupBottomSheet() {
        sheetBehavior = BottomSheetBehavior.from(bottomSheet);
        sheetBehavior.setDraggable(false);
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        sheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View sheet, int newState) {
                // Drag-down to collapsed in SETUP → return to WELCOME.
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    ExploreUiState state = viewModel.getUiState().getValue();
                    if (state != null && state.getAppState() == AppState.SETUP) {
                        viewModel.closeSetup();
                    }
                }
            }

            @Override
            public void onSlide(@NonNull View sheet, float slideOffset) {}
        });

        // Set initial slider values to match the XML placeholder text.
        sliderTime.setValues(16f, 22f);
        sliderAge.setValues(18f, 40f);
    }

    // ════════════════════════════════════════════════════════════════════
    // WELCOME CHIPS — populated dynamically from hotspot list
    // ════════════════════════════════════════════════════════════════════

    private void populateHotspotChips(List<Hotspot> hotspots) {
        chipGroupHotspots.removeAllViews();
        int count = Math.min(hotspots.size(), MAX_HOTSPOT_CHIPS);
        for (int i = 0; i < count; i++) {
            Hotspot h = hotspots.get(i);
            Chip chip = (Chip) LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_hotspot_chip, chipGroupHotspots, false);
            chip.setText(h.getName());
            chip.setOnClickListener(v -> viewModel.selectHotspot(h.getId()));
            chipGroupHotspots.addView(chip);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // CREATE INTENT FORM — listeners and submission
    // ════════════════════════════════════════════════════════════════════

    private void setupCreateIntentListeners() {
        sliderTime.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> v = slider.getValues();
            txtTimeStart.setText(formatTime(v.get(0)));
            txtTimeEnd.setText(formatTime(v.get(1)));
        });

        sliderAge.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> v = slider.getValues();
            txtAgeMin.setText(String.valueOf(v.get(0).intValue()));
            txtAgeMax.setText(String.valueOf(v.get(1).intValue()));
        });

        btnFindMatch.setOnClickListener(v -> submitCreateIntent());
    }

    private void submitCreateIntent() {
        ExploreUiState exploreState = viewModel.getUiState().getValue();
        if (exploreState == null || exploreState.getSelectedHotspot() == null) return;

        String hotspotId = exploreState.getSelectedHotspot().getId();

        List<Float> timeValues = sliderTime.getValues();
        float timeStart = timeValues.get(0);
        float timeEnd   = timeValues.get(1);

        List<Float> ageValues = sliderAge.getValues();
        int ageMin = ageValues.get(0).intValue();
        int ageMax = ageValues.get(1).intValue();

        createIntentViewModel.submit(hotspotId, timeStart, timeEnd, ageMin, ageMax);
    }

    // ════════════════════════════════════════════════════════════════════
    // PULSE ANIMATION — helpers
    // ════════════════════════════════════════════════════════════════════

    private void startPulseAnimation() {
        if (pulseOverlay == null) {
            pulseOverlay = new PulseOverlayView(requireContext());
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            mapContainer.addView(pulseOverlay, lp);
        }
        pulseOverlay.setVisibility(View.VISIBLE);
        pulseOverlay.startPulse();
    }

    private void stopPulseAnimation() {
        if (pulseOverlay != null) {
            pulseOverlay.stopPulse();
            pulseOverlay.setVisibility(View.GONE);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // STATE RENDERING — single entry point for all UI updates
    // ════════════════════════════════════════════════════════════════════

    private void renderState(ExploreUiState state) {
        // Draw markers once when the first hotspot batch arrives and map is ready.
        if (googleMap != null && markerByHotspotId.isEmpty() && !state.getHotspots().isEmpty()) {
            drawHotspotMarkers(state.getHotspots());
        }

        // Always sync the selected-marker highlight regardless of state.
        if (googleMap != null) {
            Hotspot sel = state.getSelectedHotspot();
            updateMarkerSelection(sel != null ? sel.getId() : null);
        }

        switch (state.getAppState()) {
            case WELCOME:
                stopPulseAnimation();
                welcomeContent.setVisibility(View.VISIBLE);
                setupContent.setVisibility(View.GONE);
                scanningContent.setVisibility(View.GONE);
                sheetBehavior.setDraggable(false);
                sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                if (!state.getHotspots().isEmpty()) {
                    populateHotspotChips(state.getHotspots());
                }
                break;

            case SETUP:
                stopPulseAnimation();
                welcomeContent.setVisibility(View.GONE);
                setupContent.setVisibility(View.VISIBLE);
                scanningContent.setVisibility(View.GONE);
                if (state.getSelectedHotspot() != null) {
                    txtSetupHotspotName.setText(state.getSelectedHotspot().getName());
                    zoomToHotspot(state.getSelectedHotspot());
                }
                sheetBehavior.setDraggable(true);
                sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                break;

            case SCANNING:
                welcomeContent.setVisibility(View.GONE);
                setupContent.setVisibility(View.GONE);
                scanningContent.setVisibility(View.VISIBLE);
                if (state.getSelectedHotspot() != null) {
                    txtScanningHotspotName.setText(state.getSelectedHotspot().getName());
                }
                sheetBehavior.setDraggable(false);
                sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                startPulseAnimation();
                break;
        }

        // One-shot error toast.
        if (state.getError() != null) {
            Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_SHORT).show();
            viewModel.consumeError();
        }
    }

    private void renderCreateIntentState(CreateIntentUiState state) {
        btnFindMatch.setEnabled(!state.isLoading());

        if (state.getSubmittedIntent() != null) {
            viewModel.onIntentCreated(state.getSubmittedIntent());
            return;
        }

        if (state.getError() != null) {
            Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_SHORT).show();
            createIntentViewModel.consumeError();
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
            Hotspot hotspot    = hotspotById.get(entry.getKey());
            if (hotspot == null) continue;
            Marker marker = entry.getValue();
            marker.setIcon(createMarkerIcon(hotspot.getName(), isSelected));
            marker.setZIndex(isSelected ? 20f : 10f);
        }
    }

    private com.google.android.gms.maps.model.BitmapDescriptor createMarkerIcon(
            String labelText, boolean isSelected) {
        View markerView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_map_marker, null, false);
        View pill      = markerView.findViewById(R.id.markerPill);
        TextView label = markerView.findViewById(R.id.markerLabel);
        View tail      = markerView.findViewById(R.id.markerTail);

        label.setText(labelText);
        pill.setBackgroundResource(isSelected
                ? R.drawable.bg_marker_selected : R.drawable.bg_marker_unselected);
        label.setTextColor(isSelected ? Color.WHITE : Color.parseColor("#332218"));

        GradientDrawable tailBg = new GradientDrawable();
        tailBg.setShape(GradientDrawable.RECTANGLE);
        tailBg.setColor(isSelected ? Color.parseColor("#FF7B3A") : Color.WHITE);
        tail.setBackground(tailBg);

        int widthSpec = View.MeasureSpec.makeMeasureSpec(
                requireContext().getResources().getDisplayMetrics().widthPixels,
                View.MeasureSpec.AT_MOST);
        markerView.measure(widthSpec,
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        int w = markerView.getMeasuredWidth();
        int h = markerView.getMeasuredHeight();
        if (w <= 0 || h <= 0) {
            return BitmapDescriptorFactory.defaultMarker(
                    isSelected ? BitmapDescriptorFactory.HUE_ORANGE
                               : BitmapDescriptorFactory.HUE_RED);
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
        // dimOverlay: always GONE in the new design; kept for potential future use.
        dimOverlay.setOnClickListener(v -> {
            ExploreUiState state = viewModel.getUiState().getValue();
            if (state != null && state.getAppState() == AppState.SETUP) {
                viewModel.closeSetup();
            }
        });

        // Cancel the active scan and return to WELCOME.
        btnStopSearching.setOnClickListener(v -> viewModel.resetToWelcome());
    }

    // ════════════════════════════════════════════════════════════════════
    // BACK PRESS
    // ════════════════════════════════════════════════════════════════════

    private void setupBackPressHandling() {
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (!handleBackPressed()) {
                            setEnabled(false);
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                            setEnabled(true);
                        }
                    }
                });
    }

    private boolean handleBackPressed() {
        ExploreUiState state = viewModel.getUiState().getValue();
        if (state == null) return false;
        switch (state.getAppState()) {
            case SETUP:
                viewModel.closeSetup();
                return true;
            case SCANNING:
                return true; // Back is blocked while a scan is in progress.
            default:
                return false;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // FORMAT HELPERS
    // ════════════════════════════════════════════════════════════════════

    /** Converts a float hour value (e.g. 16.5) to "HH:MM" (e.g. "16:30"). */
    private String formatTime(float val) {
        int hours   = (int) val;
        int minutes = Math.round((val - hours) * 60);
        return String.format(Locale.getDefault(), "%02d:%02d", hours, minutes);
    }

    // ════════════════════════════════════════════════════════════════════
    // ANIMATION HELPERS — available for future phases
    // ════════════════════════════════════════════════════════════════════

    protected void showWithAnim(View view, int animResId) {
        if (view.getVisibility() == View.VISIBLE) return;
        view.setVisibility(View.VISIBLE);
        view.startAnimation(
                android.view.animation.AnimationUtils.loadAnimation(requireContext(), animResId));
    }

    protected void hideWithAnim(View view, int animResId) {
        if (view.getVisibility() != View.VISIBLE) return;
        android.view.animation.Animation anim =
                android.view.animation.AnimationUtils.loadAnimation(requireContext(), animResId);
        anim.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
            @Override public void onAnimationStart(android.view.animation.Animation a) {}
            @Override public void onAnimationRepeat(android.view.animation.Animation a) {}
            @Override public void onAnimationEnd(android.view.animation.Animation a) {
                view.setVisibility(View.GONE);
            }
        });
        view.startAnimation(anim);
    }

    protected void hideView(View view) {
        view.clearAnimation();
        view.setVisibility(View.GONE);
    }
}
