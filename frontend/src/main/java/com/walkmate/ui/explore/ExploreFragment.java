package com.walkmate.ui.explore;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.walkmate.R;
import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.ui.explore.ExploreUiState.AppState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tab 1: Explore.
 *
 * Responsibilities (B1–B3):
 *   - Host a SupportMapFragment via getChildFragmentManager()
 *   - Load and display Hotspot markers on the map
 *   - Observe ExploreViewModel.uiState and react to WELCOME / SETUP / SCANNING
 *   - Forward marker-tap and back-press events to the ViewModel
 *
 * Phases B4–B6 will add the Welcome bottom sheet, the Create Intent form,
 * and the Scanning floating card respectively. Each phase only extends
 * this class — no existing logic here needs to be altered.
 */
public class ExploreFragment extends Fragment implements OnMapReadyCallback {

    public static final String TAG = "ExploreFragment";

    private static final String TAG_MAP = "explore_map_fragment";

    // ── Views ────────────────────────────────────────────────────────────
    // dimOverlay is bound but intentionally unused in B1–B3.
    // It is kept here so B5/B6 can reference it without additional binding.
    private View dimOverlay;

    // ── Map ──────────────────────────────────────────────────────────────
    private GoogleMap googleMap;
    private final Map<String, Marker> markerByHotspotId = new HashMap<>();
    private final Map<String, Hotspot> hotspotById      = new HashMap<>();

    // ── ViewModel ────────────────────────────────────────────────────────
    private ExploreViewModel viewModel;

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
        setupListeners();
        setupBackPressHandling();
    }

    // ════════════════════════════════════════════════════════════════════
    // VIEW BINDING
    // ════════════════════════════════════════════════════════════════════

    private void bindViews(View root) {
        dimOverlay = root.findViewById(R.id.dimOverlay);
    }

    // ════════════════════════════════════════════════════════════════════
    // VIEWMODEL — observe and render
    // ════════════════════════════════════════════════════════════════════

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this, new ExploreViewModelFactory(requireContext()))
                .get(ExploreViewModel.class);

        // Use getViewLifecycleOwner() — correct for Fragment LiveData observation.
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
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
            if (state != null && state.getAppState() == AppState.SETUP) {
                viewModel.closeSetup();
            }
        });

        googleMap.setOnMarkerClickListener(marker -> {
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
                // Phase B4: show the Welcome bottom sheet here.
                break;

            case SETUP:
                // Zoom the camera to the chosen hotspot immediately.
                if (state.getSelectedHotspot() != null) {
                    zoomToHotspot(state.getSelectedHotspot());
                }
                // Phase B5: show the embedded Create Intent form here.
                break;

            case SCANNING:
                // Phase B6: show the scanning floating card here.
                break;
        }

        // One-shot error toast.
        if (state.getError() != null) {
            Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_SHORT).show();
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
        // dimOverlay: dismiss the create-intent form when the user taps outside.
        // Used by Phase B5; harmless here since dimOverlay is always GONE.
        dimOverlay.setOnClickListener(v -> {
            ExploreUiState state = viewModel.getUiState().getValue();
            if (state != null && state.getAppState() == AppState.SETUP) {
                viewModel.closeSetup();
            }
        });
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
    // ANIMATION HELPERS — available for B4–B6 phases
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
