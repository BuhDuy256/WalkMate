package com.walkmate.ui.explore;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.SwitchCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.textfield.TextInputEditText;
import com.walkmate.R;
import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.ui.explore.ExploreUiState.AppState;
import com.walkmate.ui.explore.createintent.CreateIntentUiState;
import com.walkmate.ui.explore.createintent.CreateIntentViewModel;
import com.walkmate.ui.explore.createintent.CreateIntentViewModelFactory;
import com.walkmate.ui.explore.createintent.FriendPickerBottomSheet;
import com.walkmate.ui.auth.AuthActivity;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tab 1: Explore.
 *
 * State machine:
 *   WELCOME  → Welcome bottom sheet (STATE_COLLAPSED, nav bar VISIBLE, back button GONE)
 *   SETUP    → Create Intent form (STATE_EXPANDED, nav bar HIDDEN, back button VISIBLE)
 *   SCANNING → Scanning status (STATE_COLLAPSED, nav bar HIDDEN, back button GONE)
 */
public class ExploreFragment extends Fragment implements OnMapReadyCallback {

    public static final String TAG = "ExploreFragment";

    private static final String TAG_MAP = "explore_map_fragment";
    private static final int MAX_HOTSPOT_CHIPS = 5;

    // ── Views ────────────────────────────────────────────────────────────
    private View dimOverlay;
    private FrameLayout mapContainer;

    // Single back button used in both WELCOME and SETUP with state-based action.
    private FrameLayout btnBackToHome;

    // Bottom sheet ────────────────────────────────────────────────────────
    private View bottomSheetContainer;
    private HandleOnlyBottomSheetBehavior<View> sheetBehavior;
    private NestedScrollView bottomSheetScrollContent;
    // Full-width touch area whose rect gates whether a drag gesture is allowed.
    private View dragHandleArea;
    private View welcomeContent;
    private View setupContent;
    private View scanningContent;

    // Welcome content ─────────────────────────────────────────────────────
    private TextInputEditText searchInputEdit;
    private LinearLayout searchResultsContainer;
    private LinearLayout popularSpotsSection;
    private LinearLayout chipGroupHotspots;

    // Setup (Create Intent) form ──────────────────────────────────────────
    private TextView txtSetupHotspotName;
    private LinearLayout rowDatePicker;
    private TextView txtSelectedDate;
    private String selectedDateIso = "";
    private RangeSlider sliderTime;
    private RangeSlider sliderAge;
    private TextView txtTimeStart;
    private TextView txtTimeEnd;
    private TextView txtAgeMin;
    private TextView txtAgeMax;
    private LinearLayout layoutPublicOptions;
    private ChipGroup chipGroupGender;
    private TextView txtPrivateModeHint;
    private SwitchCompat switchPrivateWalk;
    private LinearLayout rowFriendPicker;
    private TextView txtSelectedFriend;
    private TextView txtPrivateIntentError;
    private MaterialButton btnFindMatch;

    // Scanning sheet ──────────────────────────────────────────────────────
    private TextView txtScanningHotspotName;
    private MaterialButton btnStopSearching;

    // ── Map ──────────────────────────────────────────────────────────────
    private GoogleMap googleMap;
    private final Map<String, Marker> markerByHotspotId = new HashMap<>();
    private final Map<String, Hotspot> hotspotById = new HashMap<>();
    // Tracks the last list drawn on the map to avoid redundant redraws.
    private List<Hotspot> lastRenderedFilteredHotspots = null;
    // Tracks last rendered app state to prevent spurious sheet-state resets.
    private ExploreUiState.AppState lastRenderedAppState = null;

    // ── Pulse animation ──────────────────────────────────────────────────
    private PulseOverlayView pulseOverlay;

    // ── ViewModels ───────────────────────────────────────────────────────
    private ExploreViewModel viewModel;
    private CreateIntentViewModel createIntentViewModel;

    // ════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════════════════

    @Nullable
    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_explore, container, false);
    }

    @Override
    public void onViewCreated(
        @NonNull View view,
        @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupViewModel();
        setupMap();
        setupBottomSheet();
        setupCreateIntentListeners();
        setupListeners();
        setupBackPressHandling();
        // Bug 7: load here (not in ViewModel constructor) so the observer is
        // already attached; guard prevents redundant reload on config change.
        if (viewModel.getUiState().getValue().getHotspots().isEmpty()) {
            viewModel.loadHotspots();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopPulseAnimation();
    }

    // ════════════════════════════════════════════════════════════════════
    // VIEW BINDING
    // ════════════════════════════════════════════════════════════════════

    private void bindViews(View root) {
        dimOverlay = root.findViewById(R.id.dimOverlay);
        mapContainer = root.findViewById(R.id.mapContainer);
        btnBackToHome = root.findViewById(R.id.btnBackToHome);

        bottomSheetContainer = root.findViewById(R.id.bottomSheetContainer);
        bottomSheetScrollContent = root.findViewById(
            R.id.bottomSheetScrollContent
        );
        dragHandleArea = root.findViewById(R.id.dragHandleArea);
        welcomeContent = root.findViewById(R.id.welcomeContent);
        setupContent = root.findViewById(R.id.setupContent);
        scanningContent = root.findViewById(R.id.scanningContent);

        searchInputEdit = root.findViewById(R.id.searchInputEdit);
        searchResultsContainer = root.findViewById(R.id.searchResultsContainer);
        popularSpotsSection = root.findViewById(R.id.popularSpotsSection);
        chipGroupHotspots = root.findViewById(R.id.chipGroupHotspots);

        txtSetupHotspotName   = root.findViewById(R.id.txtSetupHotspotName);
        rowDatePicker         = root.findViewById(R.id.rowDatePicker);
        txtSelectedDate       = root.findViewById(R.id.txtSelectedDate);
        sliderTime            = root.findViewById(R.id.sliderTime);
        sliderAge             = root.findViewById(R.id.sliderAge);
        txtTimeStart          = root.findViewById(R.id.txtTimeStart);
        txtTimeEnd            = root.findViewById(R.id.txtTimeEnd);
        txtAgeMin             = root.findViewById(R.id.txtAgeMin);
        txtAgeMax             = root.findViewById(R.id.txtAgeMax);
        btnFindMatch          = root.findViewById(R.id.btnFindMatch);
        layoutPublicOptions   = root.findViewById(R.id.layoutPublicOptions);
        chipGroupGender       = root.findViewById(R.id.chipGroupGender);
        txtPrivateModeHint    = root.findViewById(R.id.txtPrivateModeHint);
        switchPrivateWalk     = root.findViewById(R.id.switchPrivateWalk);
        rowFriendPicker       = root.findViewById(R.id.rowFriendPicker);
        txtSelectedFriend     = root.findViewById(R.id.txtSelectedFriend);
        txtPrivateIntentError = root.findViewById(R.id.txtPrivateIntentError);

        txtScanningHotspotName = root.findViewById(R.id.txtScanningHotspotName);
        btnStopSearching = root.findViewById(R.id.btnStopSearching);
    }

    // ════════════════════════════════════════════════════════════════════
    // VIEWMODEL — setup and observation
    // ════════════════════════════════════════════════════════════════════

    private void setupViewModel() {
        viewModel = new ViewModelProvider(
            this,
            new ExploreViewModelFactory(this, getArguments(), requireContext())
        ).get(ExploreViewModel.class);
        viewModel
            .getUiState()
            .observe(getViewLifecycleOwner(), this::renderState);

        createIntentViewModel = new ViewModelProvider(
            this,
            new CreateIntentViewModelFactory(requireContext())
        ).get(CreateIntentViewModel.class);
        createIntentViewModel
            .getUiState()
            .observe(getViewLifecycleOwner(), this::renderCreateIntentState);
    }

    // ════════════════════════════════════════════════════════════════════
    // MAP SETUP — uses getChildFragmentManager() (critical for Fragments)
    // ════════════════════════════════════════════════════════════════════

    private void setupMap() {
        SupportMapFragment mapFragment =
            (SupportMapFragment) getChildFragmentManager().findFragmentByTag(
                TAG_MAP
            );
        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance();
            getChildFragmentManager()
                .beginTransaction()
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
            if (
                state == null || state.getAppState() == AppState.SCANNING
            ) return;
            if (state.getAppState() == AppState.SETUP) {
                viewModel.closeSetup();
            }
        });

        googleMap.setOnMarkerClickListener(marker -> {
            ExploreUiState state = viewModel.getUiState().getValue();
            if (
                state != null && state.getAppState() == AppState.SCANNING
            ) return true;
            if (!(marker.getTag() instanceof String)) return false;
            viewModel.selectHotspot((String) marker.getTag());
            return true;
        });

        // Hotspots may have loaded before the map was ready; draw them now.
        ExploreUiState current = viewModel.getUiState().getValue();
        if (current != null && !current.getFilteredHotspots().isEmpty()) {
            drawHotspotMarkers(current.getFilteredHotspots(), true);
            lastRenderedFilteredHotspots = current.getFilteredHotspots();
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // BOTTOM SHEET SETUP
    // ════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private void setupBottomSheet() {
        sheetBehavior = (HandleOnlyBottomSheetBehavior<
            View
        >) BottomSheetBehavior.from(bottomSheetContainer);
        // Tell the behavior which view is the valid drag-start zone.
        sheetBehavior.setHandleView(dragHandleArea);
        // Draggable from the start; the custom behavior restricts it to the handle.
        sheetBehavior.setDraggable(true);
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        // Tap the handle (no drag) to toggle the sheet in WELCOME state.
        dragHandleArea.setOnClickListener(v -> {
            ExploreUiState state = viewModel.getUiState().getValue();
            if (
                state == null || state.getAppState() != AppState.WELCOME
            ) return;
            int current = sheetBehavior.getState();
            if (current == BottomSheetBehavior.STATE_COLLAPSED) {
                sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            } else if (current == BottomSheetBehavior.STATE_EXPANDED) {
                sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        sheetBehavior.addBottomSheetCallback(
            new BottomSheetBehavior.BottomSheetCallback() {
                @Override
                public void onStateChanged(@NonNull View sheet, int newState) {
                    ExploreUiState state = viewModel.getUiState().getValue();
                    if (state == null) return;

                    if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                        if (state.getAppState() == AppState.SETUP) {
                            viewModel.closeSetup();
                        }
                    }

                    // RESET: When sheet returns to collapsed (1/3 peek), lock hideable to prevent accidental dismiss next time
                    if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                        sheetBehavior.setHideable(false);
                    }
                }

                @Override
                public void onSlide(@NonNull View sheet, float slideOffset) {
                    // slideOffset: 1.0 (fully expanded), 0.0 (collapsed / 1/3 peek), -1.0 (hidden)
                    // Only allow "hide" once the user has dragged far enough down (e.g. past -0.8)
                    if (slideOffset < -0.8f) {
                        if (!sheetBehavior.isHideable()) {
                            sheetBehavior.setHideable(true);
                        }
                    } else if (slideOffset > -0.5f) {
                        // If user pulled slightly then reversed, lock hideable immediately so it snaps back to 1/3
                        sheetBehavior.setHideable(false);
                    }
                }
            }
        );

        // Initialise slider labels to match the XML default values.
        sliderTime.setValues(16f, 22f);
        sliderAge.setValues(18f, 40f);
    }

    /**
     * Sets the BottomSheetBehavior expandedOffset so that, when fully expanded,
     * the top edge of the sheet sits just below the Back button (+ 16 dp gap).
     * Called once each time we enter SETUP, after the button has been laid out.
     */
    private void applySheetExpandedOffset() {
        btnBackToHome.post(() -> {
            if (getView() == null) return;
            int[] rootLoc = new int[2];
            requireView().getLocationOnScreen(rootLoc);

            int[] btnLoc = new int[2];
            btnBackToHome.getLocationOnScreen(btnLoc);

            // Distance from top of the CoordinatorLayout to the bottom edge of the button.
            int btnBottomRelativeToRoot =
                (btnLoc[1] + btnBackToHome.getHeight()) - rootLoc[1];
            int padding16dp = (int) (16 *
                getResources().getDisplayMetrics().density);
            sheetBehavior.setExpandedOffset(
                btnBottomRelativeToRoot + padding16dp
            );
        });
    }

    // ════════════════════════════════════════════════════════════════════
    // WELCOME CHIPS — populated dynamically from hotspot list
    // ════════════════════════════════════════════════════════════════════

    private void populateHotspotChips(List<Hotspot> hotspots) {
        chipGroupHotspots.removeAllViews();
        int count = Math.min(hotspots.size(), MAX_HOTSPOT_CHIPS);
        for (int i = 0; i < count; i++) {
            Hotspot h = hotspots.get(i);
            View chip = LayoutInflater.from(requireContext()).inflate(
                R.layout.item_hotspot_chip,
                chipGroupHotspots,
                false
            );
            ((TextView) chip.findViewById(R.id.chipLabel)).setText(h.getName());
            chip.setOnClickListener(v -> viewModel.selectHotspot(h.getId()));
            chipGroupHotspots.addView(chip);
        }
    }

    /**
     * Populates the search results dropdown with matching hotspots.
     * Each row uses the same item_hotspot_chip layout as the popular-spots row,
     * stretched to full width so results read as a vertical list.
     */
    private void populateSearchResults(List<Hotspot> results) {
        searchResultsContainer.removeAllViews();

        if (results.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("No results found");
            empty.setTextColor(Color.parseColor("#A8A29E"));
            empty.setTextSize(14f);
            int pad = (int) (12 * getResources().getDisplayMetrics().density);
            empty.setPadding(0, pad, 0, pad);
            searchResultsContainer.addView(empty);
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        int gapPx = (int) (8 * density);

        for (Hotspot h : results) {
            View row = LayoutInflater.from(requireContext()).inflate(
                    R.layout.item_hotspot_chip, searchResultsContainer, false);

            // Stretch each chip to full container width for a vertical list feel.
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = gapPx;
            row.setLayoutParams(lp);

            ((TextView) row.findViewById(R.id.chipLabel)).setText(h.getName());
            row.setOnClickListener(v -> viewModel.selectHotspot(h.getId()));
            searchResultsContainer.addView(row);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // CREATE INTENT FORM — listeners and submission
    // ════════════════════════════════════════════════════════════════════

    private void setupCreateIntentListeners() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        selectedDateIso = sdf.format(cal.getTime());
        if (txtSelectedDate != null) {
            txtSelectedDate.setText(selectedDateIso);
        }
        android.view.View.OnClickListener datePickerListener = v -> {
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH);
            int day = cal.get(Calendar.DAY_OF_MONTH);

            android.app.DatePickerDialog dialog = new android.app.DatePickerDialog(
                    requireContext(), (pickerView, y, m, d) -> {
                cal.set(y, m, d);
                selectedDateIso = sdf.format(cal.getTime());
                if (txtSelectedDate != null) txtSelectedDate.setText(selectedDateIso);
            }, year, month, day);

            // User cannot choose past dates.
            dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            dialog.show();
        };
        if (rowDatePicker != null) rowDatePicker.setOnClickListener(datePickerListener);
        if (txtSelectedDate != null) txtSelectedDate.setOnClickListener(datePickerListener);

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

        switchPrivateWalk.setOnCheckedChangeListener((btn, isChecked) -> {
            createIntentViewModel.togglePrivate();
        });

        txtSelectedFriend.setOnClickListener(v -> showFriendPicker());
    }

    private void showIncompleteProfileSheet() {
        IncompleteProfileBottomSheet sheet = IncompleteProfileBottomSheet.newInstance();
        sheet.setOnCompleteProfileListener(() ->
                Navigation.findNavController(requireView())
                        .navigate(R.id.action_explore_to_editProfile));
        sheet.show(getChildFragmentManager(), IncompleteProfileBottomSheet.TAG);
    }

    private void showFriendPicker() {
        CreateIntentUiState s = createIntentViewModel.getUiState().getValue();
        if (s == null) return;

        FriendPickerBottomSheet sheet = FriendPickerBottomSheet.newInstance();
        sheet.setOnFriendSelectedListener((userId, name) ->
                createIntentViewModel.selectFriend(userId, name));
        sheet.setFriends(s.getFriendList(), s.isFriendListLoading());
        sheet.show(getChildFragmentManager(), FriendPickerBottomSheet.TAG);
    }

    private void submitCreateIntent() {
        ExploreUiState exploreState = viewModel.getUiState().getValue();
        if (
            exploreState == null || exploreState.getSelectedHotspot() == null
        ) return;

        String hotspotId = exploreState.getSelectedHotspot().getId();

        List<Float> timeValues = sliderTime.getValues();
        float timeStart = timeValues.get(0);
        float timeEnd = timeValues.get(1);

        // Check if the selected time is in the past
        Calendar today = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String todayIso = sdf.format(today.getTime());

        if (selectedDateIso.equals(todayIso)) {
            float currentHourVal = today.get(Calendar.HOUR_OF_DAY) + (today.get(Calendar.MINUTE) / 60f);
            if (timeStart < currentHourVal) {
                Toast.makeText(requireContext(), "Start time cannot be in the past", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        List<Float> ageValues = sliderAge.getValues();
        int ageMin = ageValues.get(0).intValue();
        int ageMax = ageValues.get(1).intValue();

        CreateIntentUiState intentState = createIntentViewModel.getUiState().getValue();
        boolean isPrivate = intentState != null && intentState.isPrivate();
        String invitedFriendId = intentState != null ? intentState.getInvitedFriendId() : null;

        String preferredGender = resolvePreferredGender();

        createIntentViewModel.submit(
            hotspotId,
            selectedDateIso,
            timeStart,
            timeEnd,
            ageMin,
            ageMax,
            preferredGender,
            java.util.Collections.emptyList(),
            isPrivate,
            invitedFriendId
        );
    }

    // ════════════════════════════════════════════════════════════════════
    // PULSE ANIMATION — helpers
    // ════════════════════════════════════════════════════════════════════

    private void startPulseAnimation() {
        if (pulseOverlay == null) {
            pulseOverlay = new PulseOverlayView(requireContext());
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            );
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
        // Auth gate: navigate to login when user taps a hotspot without being signed in.
        String pendingHotspot = state.getPendingHotspotId();
        if (pendingHotspot != null) {
            viewModel.consumePendingHotspot();
            startActivity(new Intent(requireContext(), AuthActivity.class));
            return;
        }

        // Redraw map markers whenever the filtered list changes.
        if (googleMap != null) {
            List<Hotspot> filtered = state.getFilteredHotspots();
            if (hasFilteredHotspotsChanged(filtered)) {
                boolean fitCamera = (lastRenderedFilteredHotspots == null);
                drawHotspotMarkers(filtered, fitCamera);
                lastRenderedFilteredHotspots = filtered;
            }
        }

        // Sync selected-marker highlight for every state transition.
        if (googleMap != null) {
            Hotspot sel = state.getSelectedHotspot();
            updateMarkerSelection(sel != null ? sel.getId() : null);
        }

        boolean stateChanged = (state.getAppState() != lastRenderedAppState);
        lastRenderedAppState = state.getAppState();

        switch (state.getAppState()) {
            case WELCOME:
                stopPulseAnimation();

                // Bottom nav is always hidden while ExploreFragment is on screen;
                // MainActivity hides it automatically on navigation. Back button
                // is shown in WELCOME so the user can return to Home.
                btnBackToHome.setVisibility(View.VISIBLE);

                welcomeContent.setVisibility(View.VISIBLE);
                setupContent.setVisibility(View.GONE);
                scanningContent.setVisibility(View.GONE);

                // Only touch sheet state when actually transitioning into WELCOME.
                // Calling setState() on every filterHotspots() update collapses the
                // sheet while the user is still typing.
                if (stateChanged) {
                    sheetBehavior.setDraggable(true);
                    sheetBehavior.setHideable(false);
                    sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    // Clear stale search text when returning from SETUP / SCANNING.
                    searchInputEdit.setText("");
                }

                // Chips always show the full popular-spots list.
                populateHotspotChips(state.getHotspots());

                // Below the search bar: show results list OR popular-spots panel.
                String query = searchInputEdit.getText() != null
                        ? searchInputEdit.getText().toString() : "";
                if (query.isEmpty()) {
                    popularSpotsSection.setVisibility(View.VISIBLE);
                    searchResultsContainer.setVisibility(View.GONE);
                } else {
                    popularSpotsSection.setVisibility(View.GONE);
                    searchResultsContainer.setVisibility(View.VISIBLE);
                    populateSearchResults(state.getFilteredHotspots());
                }
                break;
            case SETUP:
                stopPulseAnimation();

                // Single back button remains visible in SETUP; action switches to close setup.
                btnBackToHome.setVisibility(View.VISIBLE);

                welcomeContent.setVisibility(View.GONE);
                setupContent.setVisibility(View.VISIBLE);
                scanningContent.setVisibility(View.GONE);

                if (stateChanged) {
                    applySheetExpandedOffset();

                    bottomSheetContainer.post(() -> {
                        // Force the container to re-measure so it fully recognises the new SETUP content
                        bottomSheetContainer.requestLayout();

                        // Always scroll to the top so the user sees the title and content isn't drifted
                        bottomSheetScrollContent.scrollTo(0, 0);
                    });

                    sheetBehavior.setDraggable(true);
                    sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    sheetBehavior.setHideable(true);
                }

                if (state.getSelectedHotspot() != null) {
                    txtSetupHotspotName.setText(
                        state.getSelectedHotspot().getName()
                    );
                    zoomToHotspot(state.getSelectedHotspot());
                }
                break;
            case SCANNING:
                // Hide back button while scanning — only btnStopSearching can exit.
                btnBackToHome.setVisibility(View.GONE);

                welcomeContent.setVisibility(View.GONE);
                setupContent.setVisibility(View.GONE);
                scanningContent.setVisibility(View.VISIBLE);

                if (state.getSelectedHotspot() != null) {
                    txtScanningHotspotName.setText(
                        state.getSelectedHotspot().getName()
                    );
                }

                if (stateChanged) {
                    sheetBehavior.setDraggable(false);
                    sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
                startPulseAnimation();
                break;
        }

        // One-shot error toast.
        if (state.getError() != null) {
            Toast.makeText(
                requireContext(),
                state.getError(),
                Toast.LENGTH_SHORT
            ).show();
            viewModel.consumeError();
        }

        // Match found inline (private invite or immediate public match) — Proposal tab.
        if (state.getMatchFoundProposalId() != null) {
            navigateToMatchesTab(com.walkmate.ui.matches.MatchesPagerAdapter.TAB_PROPOSAL);
            viewModel.consumeMatchFound();
        }

        // No immediate match — intent is OPEN, navigate to Finding tab.
        if (state.isIntentOpenPending()) {
            navigateToMatchesTab(com.walkmate.ui.matches.MatchesPagerAdapter.TAB_FINDING);
            viewModel.consumeIntentOpen();
        }

        // Phase 4 — timeout: show "Still looking…" dialog (only once per timeout).
        if (state.isScanTimedOut()) {
            showScanTimeoutDialog();
        }
    }

    /**
     * Refreshes all Matches data and navigates to the given sub-tab.
     * Called every time ExploreFragment transitions the user to the Matches screen
     * so the data is always fresh (Finding list, Proposals, Sessions).
     */
    private void navigateToMatchesTab(int tabIndex) {
        Bundle args = new Bundle();
        args.putInt("scrollToTab", tabIndex);
        Navigation.findNavController(requireView()).navigate(R.id.matchesFragment, args);
    }

    /** Shows the "Still looking…" bottom-sheet dialog on scan timeout. */
    private void showScanTimeoutDialog() {
        // Dismiss immediately in state so a rotation doesn't re-show the dialog.
        viewModel.dismissTimeout();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Still looking\u2026")
                .setMessage("No match found yet. Keep your search active while you explore?")
                .setPositiveButton("Keep Searching", (d, w) -> { /* stay in SCANNING */ })
                .setNegativeButton("Save to Finding List", (d, w) -> {
                    Bundle args = new Bundle();
                    args.putInt("scrollToTab",
                            com.walkmate.ui.matches.MatchesPagerAdapter.TAB_FINDING);
                    Navigation.findNavController(requireView())
                            .navigate(R.id.matchesFragment, args);
                })
                .setCancelable(false)
                .show();
    }

    private void renderCreateIntentState(CreateIntentUiState state) {
        // Loading: disable the submit button while the API call is in-flight.
        if (btnFindMatch != null) btnFindMatch.setEnabled(!state.isLoading());

        // Submission complete — hand off to ExploreViewModel for navigation decision,
        // then immediately consume so this is not re-triggered on rotation.
        if (state.getSubmittedIntent() != null) {
            viewModel.onIntentCreated(state.getSubmittedIntent());
            createIntentViewModel.consumeSubmission();
            return;
        }

        // Sync the private-walk switch without re-triggering the listener
        if (switchPrivateWalk != null && switchPrivateWalk.isChecked() != state.isPrivate()) {
            switchPrivateWalk.setOnCheckedChangeListener(null);
            switchPrivateWalk.setChecked(state.isPrivate());
            switchPrivateWalk.setOnCheckedChangeListener((btn, isChecked) ->
                    createIntentViewModel.togglePrivate());
        }

        // Public-option fields (Age / Gender / Tags) — dim and block when private mode is ON.
        applyPrivateModeUx(state.isPrivate());

        // Show/hide friend picker row based on private mode
        if (rowFriendPicker != null) {
            rowFriendPicker.setVisibility(state.isPrivate() ? View.VISIBLE : View.GONE);
        }

        // Update selected friend label
        if (txtSelectedFriend != null) {
            String friendName = state.getInvitedFriendName();
            txtSelectedFriend.setText(
                    friendName != null ? friendName : getString(R.string.select_friend));
        }

        // Private intent validation error
        if (txtPrivateIntentError != null) {
            String privateErr = state.getPrivateIntentError();
            if (privateErr != null) {
                txtPrivateIntentError.setText(privateErr);
                txtPrivateIntentError.setVisibility(View.VISIBLE);
            } else {
                txtPrivateIntentError.setVisibility(View.GONE);
            }
        }

        // Update the open friend picker sheet if visible
        FriendPickerBottomSheet pickerSheet =
                (FriendPickerBottomSheet) getChildFragmentManager()
                        .findFragmentByTag(FriendPickerBottomSheet.TAG);
        if (pickerSheet != null) {
            pickerSheet.setFriends(state.getFriendList(), state.isFriendListLoading());
        }

        // Onboarding gate: backend rejected the intent because age or walk tags are missing.
        // Show a bottom sheet prompt instead of navigating away immediately.
        if (state.isOnboardingRequired()) {
            createIntentViewModel.consumeOnboardingRequired();
            showIncompleteProfileSheet();
            return;
        }

        if (state.getError() != null) {
            Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_SHORT).show();
            createIntentViewModel.consumeError();
        }
    }

    /**
     * Dims and blocks interaction on the public-only preference block (Age / Gender / Tags)
     * when private walk mode is active. Restores them when private mode is off.
     */
    private void applyPrivateModeUx(boolean isPrivate) {
        if (layoutPublicOptions != null) {
            layoutPublicOptions.setAlpha(isPrivate ? 0.35f : 1.0f);
            setViewGroupEnabled(layoutPublicOptions, !isPrivate);
        }
        if (txtPrivateModeHint != null) {
            txtPrivateModeHint.setVisibility(isPrivate ? View.VISIBLE : View.GONE);
        }
    }

    /** Recursively enables or disables all views within a ViewGroup. */
    private void setViewGroupEnabled(android.view.ViewGroup group, boolean enabled) {
        group.setEnabled(enabled);
        for (int i = 0; i < group.getChildCount(); i++) {
            android.view.View child = group.getChildAt(i);
            child.setEnabled(enabled);
            if (child instanceof android.view.ViewGroup) {
                setViewGroupEnabled((android.view.ViewGroup) child, enabled);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // MAP HELPERS
    // ════════════════════════════════════════════════════════════════════

    private void drawHotspotMarkers(List<Hotspot> hotspots, boolean fitCamera) {
        googleMap.clear();
        hotspotById.clear();
        markerByHotspotId.clear();

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (Hotspot hotspot : hotspots) {
            hotspotById.put(hotspot.getId(), hotspot);
            LatLng position = new LatLng(hotspot.getLat(), hotspot.getLng());
            boundsBuilder.include(position);

            // GAP-20: scale pin by open-intent count so busy hotspots stand out
            int count = hotspot.getopenIntentCount();
            float scale = count == 0 ? 1.0f : (count <= 4 ? 1.3f : 1.6f);
            com.google.android.gms.maps.model.BitmapDescriptor pinIcon =
                com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(
                    scaleBitmap(vectorToBitmap(R.drawable.ic_hotspot_pin), scale));

            Marker marker = googleMap.addMarker(
                new MarkerOptions()
                    .position(position)
                    .title(hotspot.getName())
                    .anchor(0.5f, 1f)
                    .icon(pinIcon)
            );

            if (marker != null) {
                marker.setTag(hotspot.getId());
                markerByHotspotId.put(hotspot.getId(), marker);
            }
        }

        if (fitCamera && !hotspots.isEmpty()) {
            googleMap.moveCamera(
                CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 180)
            );
        }
    }

    /**
     * Converts a vector drawable resource to a {@link Bitmap}.
     * {@link BitmapFactory#decodeResource} returns null for XML vector drawables,
     * so we draw the drawable onto a Canvas instead.
     */
    private Bitmap vectorToBitmap(@androidx.annotation.DrawableRes int resId) {
        android.graphics.drawable.Drawable drawable =
                androidx.core.content.ContextCompat.getDrawable(requireContext(), resId);
        if (drawable == null) return null;
        Bitmap bitmap = Bitmap.createBitmap(
                drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    /**
     * Scales {@code src} by the given factor using bilinear filtering.
     * Used to reflect open-intent count as pin visual weight (GAP-20).
     */
    private Bitmap scaleBitmap(Bitmap src, float scale) {
        if (src == null) return null;
        int w = (int) (src.getWidth()  * scale);
        int h = (int) (src.getHeight() * scale);
        return Bitmap.createScaledBitmap(src, w, h, true);
    }

    /**
     * Returns true if {@code current} differs from the last list drawn on the map.
     * Compares by hotspot ID sequence so order changes also trigger a redraw.
     */
    private boolean hasFilteredHotspotsChanged(List<Hotspot> current) {
        if (lastRenderedFilteredHotspots == null) return !current.isEmpty();
        if (lastRenderedFilteredHotspots.size() != current.size()) return true;
        for (int i = 0; i < current.size(); i++) {
            if (!current.get(i).getId().equals(lastRenderedFilteredHotspots.get(i).getId())) {
                return true;
            }
        }
        return false;
    }

    private void zoomToHotspot(Hotspot hotspot) {
        if (googleMap == null) return;
        LatLng target = new LatLng(hotspot.getLat(), hotspot.getLng());
        googleMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(target, 15f),
            400,
            null
        );
    }

    private void updateMarkerSelection(String selectedId) {
        for (Map.Entry<String, Marker> entry : markerByHotspotId.entrySet()) {
            boolean isSelected = entry.getKey().equals(selectedId);
            Hotspot hotspot = hotspotById.get(entry.getKey());
            if (hotspot == null) continue;
            entry
                .getValue()
                .setIcon(createMarkerIcon(hotspot.getName(), isSelected));
            entry.getValue().setZIndex(isSelected ? 20f : 10f);
        }
    }

    private com.google.android.gms.maps.model.BitmapDescriptor createMarkerIcon(
        String labelText,
        boolean isSelected
    ) {
        View markerView = LayoutInflater.from(requireContext()).inflate(
            R.layout.item_map_marker,
            null,
            false
        );
        View pill = markerView.findViewById(R.id.markerPill);
        TextView label = markerView.findViewById(R.id.markerLabel);
        View tail = markerView.findViewById(R.id.markerTail);

        label.setText(labelText);
        pill.setBackgroundResource(
            isSelected
                ? R.drawable.bg_marker_selected
                : R.drawable.bg_marker_unselected
        );
        label.setTextColor(
            isSelected ? Color.WHITE : Color.parseColor("#332218")
        );

        GradientDrawable tailBg = new GradientDrawable();
        tailBg.setShape(GradientDrawable.RECTANGLE);
        tailBg.setColor(isSelected ? Color.parseColor("#FF7B3A") : Color.WHITE);
        tail.setBackground(tailBg);

        int widthSpec = View.MeasureSpec.makeMeasureSpec(
            requireContext().getResources().getDisplayMetrics().widthPixels,
            View.MeasureSpec.AT_MOST
        );
        markerView.measure(
            widthSpec,
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );

        int w = markerView.getMeasuredWidth();
        int h = markerView.getMeasuredHeight();
        if (w <= 0 || h <= 0) {
            return BitmapDescriptorFactory.defaultMarker(
                isSelected
                    ? BitmapDescriptorFactory.HUE_ORANGE
                    : BitmapDescriptorFactory.HUE_RED
            );
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
        // Single back button with state-based behavior.
        btnBackToHome.setOnClickListener(v -> {
            ExploreUiState state = viewModel.getUiState().getValue();
            if (state != null && state.getAppState() == AppState.SETUP) {
                viewModel.closeSetup();
                return;
            }
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        });

        // Tapping the map while in SETUP closes the form.
        dimOverlay.setOnClickListener(v -> {
            ExploreUiState state = viewModel.getUiState().getValue();
            if (state != null && state.getAppState() == AppState.SETUP) {
                viewModel.closeSetup();
            }
        });

        // Cancel the active scan: cancels backend intent and returns to WELCOME.
        btnStopSearching.setOnClickListener(v -> viewModel.stopSearching());

        // Expand the sheet when the search field gains focus (Google Maps-like UX).
        searchInputEdit.setOnFocusChangeListener((v, hasFocus) -> {
            ExploreUiState s = viewModel.getUiState().getValue();
            if (s == null || s.getAppState() != AppState.WELCOME) return;
            if (hasFocus) {
                sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        // Search field — filter hotspot list in real-time; results shown as dropdown.
        searchInputEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.filterHotspots(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    // ════════════════════════════════════════════════════════════════════
    // BACK PRESS
    // ════════════════════════════════════════════════════════════════════

    private void setupBackPressHandling() {
        requireActivity()
            .getOnBackPressedDispatcher()
            .addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (!handleBackPressed()) {
                            setEnabled(false);
                            requireActivity()
                                .getOnBackPressedDispatcher()
                                .onBackPressed();
                            setEnabled(true);
                        }
                    }
                }
            );
    }

    private boolean handleBackPressed() {
        ExploreUiState state = viewModel.getUiState().getValue();
        if (state == null) return false;
        switch (state.getAppState()) {
            case SETUP:
                // Delegate to ViewModel — this triggers WELCOME which re-shows the nav bar.
                viewModel.closeSetup();
                return true;
            case SCANNING:
                // Block back press while a scan is in progress; use btnStopSearching.
                return true;
            default:
                return false;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // FORMAT HELPERS
    // ════════════════════════════════════════════════════════════════════

    /**
     * Returns the API gender preference value based on the selected chip in chipGroupGender.
     * "Any" chip is the default (pre-checked in XML); falls back to "ANY" if nothing is selected.
     */
    private String resolvePreferredGender() {
        if (chipGroupGender == null) return "ANY";
        int checkedId = chipGroupGender.getCheckedChipId();
        if (checkedId == R.id.chipMale)   return "MALE";
        if (checkedId == R.id.chipFemale) return "FEMALE";
        return "ANY";
    }

    /** Converts a float hour value (e.g. 16.5) to "HH:MM" (e.g. "16:30"). */
    private String formatTime(float val) {
        int hours = (int) val;
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
            android.view.animation.AnimationUtils.loadAnimation(
                requireContext(),
                animResId
            )
        );
    }

    protected void hideWithAnim(View view, int animResId) {
        if (view.getVisibility() != View.VISIBLE) return;
        android.view.animation.Animation anim =
            android.view.animation.AnimationUtils.loadAnimation(
                requireContext(),
                animResId
            );
        anim.setAnimationListener(
            new android.view.animation.Animation.AnimationListener() {
                @Override
                public void onAnimationStart(
                    android.view.animation.Animation a
                ) {}

                @Override
                public void onAnimationRepeat(
                    android.view.animation.Animation a
                ) {}

                @Override
                public void onAnimationEnd(android.view.animation.Animation a) {
                    view.setVisibility(View.GONE);
                }
            }
        );
        view.startAnimation(anim);
    }

    protected void hideView(View view) {
        view.clearAnimation();
        view.setVisibility(View.GONE);
    }
}
