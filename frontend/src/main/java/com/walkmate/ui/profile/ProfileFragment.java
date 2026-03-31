package com.walkmate.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.walkmate.R;
import com.walkmate.WalkMateApplication;

import java.util.List;

/**
 * Thin view for the Profile tab.
 *
 * Responsibilities:
 *   1. Inflate fragment_profile.xml.
 *   2. Wire click listeners — all delegate to the ViewModel; no business logic here.
 *   3. Observe LiveData<ProfileUiState> and call renderState().
 *   4. renderState() is the single place that writes to Views.
 */
public class ProfileFragment extends Fragment {

    public static final String TAG = "ProfileFragment";

    // ── Views ─────────────────────────────────────────────────────────────────

    private ImageView imgProfileAvatar;
    private View viewOnlineStatus;
    private TextView txtProfileName;
    private Chip chipTrustScore;
    private ChipGroup chipGroupTags;
    private Chip chipTag1;
    private Chip chipTag2;
    private Chip chipTag3;

    // Milestone stats
    private TextView txtStatKmValue;
    private TextView txtStatSessionsValue;
    private TextView txtStatStreakValue;

    // Badges
    private ImageView imgBadge1;
    private ImageView imgBadge2;
    private ImageView imgBadge3;
    private TextView lblBadge1;
    private TextView lblBadge2;
    private TextView lblBadge3;

    // Menu rows
    private View menuWalkHistory;
    private View menuMyBadges;
    private View menuSettings;

    // ── MVVM ──────────────────────────────────────────────────────────────────

    private ProfileViewModel viewModel;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupViewModel();
        setupClickListeners();

        viewModel.loadProfile();
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
    }

    // ── Setup helpers ─────────────────────────────────────────────────────────

    private void bindViews(View root) {
        imgProfileAvatar    = root.findViewById(R.id.imgProfileAvatar);
        viewOnlineStatus    = root.findViewById(R.id.viewOnlineStatus);
        txtProfileName      = root.findViewById(R.id.txtProfileName);
        chipTrustScore      = root.findViewById(R.id.chipTrustScore);
        chipGroupTags       = root.findViewById(R.id.chipGroupTags);
        chipTag1            = root.findViewById(R.id.chipTag1);
        chipTag2            = root.findViewById(R.id.chipTag2);
        chipTag3            = root.findViewById(R.id.chipTag3);

        txtStatKmValue       = root.findViewById(R.id.txtStatKmValue);
        txtStatSessionsValue = root.findViewById(R.id.txtStatSessionsValue);
        txtStatStreakValue   = root.findViewById(R.id.txtStatStreakValue);

        imgBadge1 = root.findViewById(R.id.imgBadge1);
        imgBadge2 = root.findViewById(R.id.imgBadge2);
        imgBadge3 = root.findViewById(R.id.imgBadge3);
        lblBadge1 = root.findViewById(R.id.lblBadge1);
        lblBadge2 = root.findViewById(R.id.lblBadge2);
        lblBadge3 = root.findViewById(R.id.lblBadge3);

        menuWalkHistory = root.findViewById(R.id.menuWalkHistory);
        menuMyBadges    = root.findViewById(R.id.menuMyBadges);
        menuSettings    = root.findViewById(R.id.menuSettings);
    }

    private void setupViewModel() {
        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        ProfileViewModelFactory factory = new ProfileViewModelFactory(app.getUserRepository());
        viewModel = new ViewModelProvider(this, factory).get(ProfileViewModel.class);
    }

    private void setupClickListeners() {
        menuWalkHistory.setOnClickListener(v -> viewModel.onWalkHistoryClicked());
        menuMyBadges.setOnClickListener(v -> viewModel.onMyBadgesClicked());
        menuSettings.setOnClickListener(v -> viewModel.onSettingsClicked());
    }

    // ── State rendering ───────────────────────────────────────────────────────

    /**
     * Single source of truth for all View mutations.
     * Called every time the LiveData emits a new ProfileUiState.
     */
    private void renderState(ProfileUiState state) {
        if (state.isLoading()) {
            // Content stays at its initial state while loading.
            return;
        }

        if (state.getError() != null) {
            Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_SHORT).show();
            return;
        }

        // ── Avatar ──
        if (state.getAvatarUrl() != null && !state.getAvatarUrl().isEmpty()) {
            Glide.with(this)
                    .load(state.getAvatarUrl())
                    .circleCrop()
                    .placeholder(R.drawable.ic_user)
                    .into(imgProfileAvatar);
        } else {
            imgProfileAvatar.setImageResource(R.drawable.ic_user);
        }

        // ── Online dot ──
        viewOnlineStatus.setVisibility(state.isOnline() ? View.VISIBLE : View.GONE);

        // ── Name ──
        if (state.getName() != null) {
            txtProfileName.setText(state.getName());
        }

        // ── Trust score chip ──
        chipTrustScore.setText(
                getString(R.string.profile_trust_score_format, state.getTrustScore()));

        // ── Personality tag chips (up to 3 slots in the layout) ──
        renderTagChips(state.getPersonalityTags());

        // ── Milestone stats ──
        txtStatKmValue.setText(String.valueOf((int) state.getTotalDistanceKm()));
        txtStatSessionsValue.setText(String.valueOf(state.getTotalSessions()));
        txtStatStreakValue.setText(String.valueOf(state.getCurrentStreak()));

        // ── Badges (up to 3 slots in the layout) ──
        renderBadges(state.getBadges());
    }

    /**
     * Populates up to 3 static chip slots with personality tags.
     * Hides any slot that has no corresponding tag.
     */
    private void renderTagChips(List<String> tags) {
        Chip[] slots = {chipTag1, chipTag2, chipTag3};
        for (int i = 0; i < slots.length; i++) {
            if (tags != null && i < tags.size()) {
                slots[i].setText(tags.get(i));
                slots[i].setVisibility(View.VISIBLE);
            } else {
                slots[i].setVisibility(View.GONE);
            }
        }
    }

    /**
     * Populates up to 3 static badge slots with icon + label.
     * Hides any slot that has no corresponding badge.
     * Uses resource IDs directly — no string comparison at runtime.
     */
    private void renderBadges(List<ProfileUiState.Badge> badges) {
        ImageView[] iconSlots = {imgBadge1, imgBadge2, imgBadge3};
        TextView[] labelSlots = {lblBadge1, lblBadge2, lblBadge3};

        for (int i = 0; i < iconSlots.length; i++) {
            View parent = (View) iconSlots[i].getParent();
            if (badges != null && i < badges.size()) {
                ProfileUiState.Badge badge = badges.get(i);
                iconSlots[i].setImageResource(badge.iconDrawableResId);
                labelSlots[i].setText(badge.labelStringResId);
                parent.setVisibility(View.VISIBLE);
            } else {
                parent.setVisibility(View.GONE);
            }
        }
    }
}
