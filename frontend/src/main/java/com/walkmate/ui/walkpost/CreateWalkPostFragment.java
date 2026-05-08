package com.walkmate.ui.walkpost;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.BuildConfig;
import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.designsystem.view.VisibilitySelectorView;
import com.walkmate.core.designsystem.view.WalkResultPostCard;
import com.walkmate.core.util.WindowInsetUtils;
import com.walkmate.domain.walkpost.PostVisibility;
import com.walkmate.domain.walkpost.WalkPost;

public class CreateWalkPostFragment extends Fragment {

    public static final String ARG_SESSION_ID       = "SESSION_ID";
    public static final String ARG_PARTNER_NAME     = "PARTNER_NAME";
    public static final String ARG_MY_WALK_ONLY     = "MY_WALK_ONLY";
    public static final String ARG_DISTANCE_KM      = "DISTANCE_KM";
    public static final String ARG_DURATION_SECONDS = "DURATION_SECONDS";
    public static final String ARG_HOTSPOT_NAME     = "HOTSPOT_NAME";
    public static final String ARG_LAT              = "LAT";
    public static final String ARG_LNG              = "LNG";

    public static CreateWalkPostFragment newInstance(
            String sessionId,
            @Nullable String partnerName,
            boolean myWalkOnly,
            double distanceKm,
            long durationSeconds,
            String hotspotName,
            double lat,
            double lng) {
        CreateWalkPostFragment f = new CreateWalkPostFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID, sessionId);
        if (partnerName != null) args.putString(ARG_PARTNER_NAME, partnerName);
        args.putBoolean(ARG_MY_WALK_ONLY, myWalkOnly);
        args.putDouble(ARG_DISTANCE_KM, distanceKm);
        args.putLong(ARG_DURATION_SECONDS, durationSeconds);
        if (hotspotName != null) args.putString(ARG_HOTSPOT_NAME, hotspotName);
        args.putDouble(ARG_LAT, lat);
        args.putDouble(ARG_LNG, lng);
        f.setArguments(args);
        return f;
    }

    // ── Views ──────────────────────────────────────────────────────────────────

    private EditText etCaption;
    private TextView txtCaptionCount;
    private VisibilitySelectorView visibilitySelector;
    private SwitchCompat switchRouteMap;
    private SwitchCompat switchStats;
    private SwitchCompat switchCompanion;
    private View layoutCompanionToggle;
    private WalkResultPostCard cardPreview;
    private Button btnPostToProfile;
    private ProgressBar progressCreatePost;
    private View scrollCreatePost;

    // ── State ──────────────────────────────────────────────────────────────────

    private CreateWalkPostViewModel viewModel;
    private String sessionId;
    private String partnerName;
    private boolean myWalkOnly;
    private double distanceKm;
    private long durationSeconds;
    private String hotspotName;
    private double lat;
    private double lng;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_create_walk_post, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        WindowInsetUtils.applyStatusBarPadding(view.findViewById(R.id.subPageHeader));

        etCaption            = view.findViewById(R.id.etCaption);
        txtCaptionCount      = view.findViewById(R.id.txtCaptionCount);
        visibilitySelector   = view.findViewById(R.id.visibilitySelector);
        switchRouteMap       = view.findViewById(R.id.switchRouteMap);
        switchStats          = view.findViewById(R.id.switchStats);
        switchCompanion      = view.findViewById(R.id.switchCompanion);
        layoutCompanionToggle = view.findViewById(R.id.layoutCompanionToggle);
        cardPreview          = view.findViewById(R.id.cardPreview);
        btnPostToProfile     = view.findViewById(R.id.btnPostToProfile);
        progressCreatePost   = view.findViewById(R.id.progressCreatePost);
        scrollCreatePost     = view.findViewById(R.id.scrollCreatePost);

        view.findViewById(R.id.btnSubPageBack)
                .setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        ((TextView) view.findViewById(R.id.txtSubPageTitle)).setText("Share Walk");

        // Read arguments
        Bundle args = getArguments();
        sessionId       = args != null ? args.getString(ARG_SESSION_ID) : null;
        partnerName     = args != null ? args.getString(ARG_PARTNER_NAME) : null;
        myWalkOnly      = args == null || args.getBoolean(ARG_MY_WALK_ONLY, false);
        distanceKm      = args != null ? args.getDouble(ARG_DISTANCE_KM, 0.0) : 0.0;
        durationSeconds = args != null ? args.getLong(ARG_DURATION_SECONDS, 0L) : 0L;
        hotspotName     = args != null ? args.getString(ARG_HOTSPOT_NAME) : null;
        lat             = args != null ? args.getDouble(ARG_LAT, 0.0) : 0.0;
        lng             = args != null ? args.getDouble(ARG_LNG, 0.0) : 0.0;

        // Companion toggle only shown when there is a partner
        if (myWalkOnly || partnerName == null) {
            layoutCompanionToggle.setVisibility(View.GONE);
        } else {
            TextView txtCompanionSubtitle = view.findViewById(R.id.txtCompanionSubtitle);
            txtCompanionSubtitle.setText("Tag " + partnerName);
        }

        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        viewModel = new ViewModelProvider(this,
                new CreateWalkPostViewModelFactory(app.getWalkPostRepository()))
                .get(CreateWalkPostViewModel.class);

        // Caption character counter + live preview refresh
        etCaption.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                int len = s.length();
                txtCaptionCount.setText(len + " / 150");
                refreshPreview();
            }
        });

        // Toggle listeners — refresh preview on any change
        switchRouteMap.setOnCheckedChangeListener((btn, checked) -> refreshPreview());
        switchStats.setOnCheckedChangeListener((btn, checked) -> refreshPreview());
        switchCompanion.setOnCheckedChangeListener((btn, checked) -> refreshPreview());
        visibilitySelector.setOnVisibilitySelectedListener(v -> refreshPreview());

        refreshPreview();

        // Submit
        btnPostToProfile.setOnClickListener(v -> submit());

        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            boolean loading = state.kind == CreateWalkPostUiState.Kind.LOADING;
            progressCreatePost.setVisibility(loading ? View.VISIBLE : View.GONE);
            scrollCreatePost.setVisibility(loading ? View.GONE : View.VISIBLE);
            btnPostToProfile.setEnabled(!loading);

            if (state.kind == CreateWalkPostUiState.Kind.SUCCESS) {
                Toast.makeText(requireContext(), "Posted!", Toast.LENGTH_SHORT).show();
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
            } else if (state.kind == CreateWalkPostUiState.Kind.ERROR && state.error != null) {
                Toast.makeText(requireContext(), state.error, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void refreshPreview() {
        String caption = etCaption.getText().toString().trim();
        boolean showRouteMap  = switchRouteMap.isChecked();
        boolean showStats     = switchStats.isChecked();
        boolean showCompanion = !myWalkOnly && partnerName != null && switchCompanion.isChecked();
        PostVisibility visibility = visibilitySelector.getSelectedVisibility();

        WalkPost preview = new WalkPost(
                null, sessionId, null,
                "You", null,
                caption.isEmpty() ? null : caption,
                visibility,
                hotspotName != null ? hotspotName : "",
                distanceKm, durationSeconds, 0,
                showCompanion, showRouteMap, showStats,
                partnerName, buildStaticMapUrl(lat, lng),
                "Today");

        cardPreview.bind(preview, WalkResultPostCard.Variant.OWNER);
    }

    private static String buildStaticMapUrl(double lat, double lng) {
        String key = BuildConfig.MAPS_API_KEY;
        if (key == null || key.isEmpty() || (lat == 0.0 && lng == 0.0)) return null;
        return "https://maps.googleapis.com/maps/api/staticmap"
                + "?center=" + lat + "," + lng
                + "&zoom=15"
                + "&size=600x300"
                + "&maptype=roadmap"
                + "&markers=color:orange%7C" + lat + "," + lng
                + "&key=" + key;
    }

    private void submit() {
        if (sessionId == null) return;
        viewModel.submit(
                sessionId,
                etCaption.getText().toString(),
                visibilitySelector.getSelectedVisibility(),
                switchRouteMap.isChecked(),
                switchStats.isChecked(),
                !myWalkOnly && partnerName != null && switchCompanion.isChecked());
    }
}
