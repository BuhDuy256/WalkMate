package com.walkmate.ui.profile;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.util.GlideHelper;
import com.walkmate.domain.user.VisibilityMode;
import com.walkmate.ui.profile.edit.EditProfileFragment;

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

    // Edit profile entry point
    private View btnEditProfile;

    // Menu rows
    private View menuWalkHistory;
    private View menuMyBadges;
    private View menuLeaderboard;
    private View menuSettings;
    private View menuFriends;
    private View menuBlockedUsers;

    // Content root (receives dynamic bottom padding from window insets)
    private View profileContentRoot;

    // Security section
    private SwitchMaterial switchVisibility;
    private View btnLogoutAll;

    // Suppress the switch listener re-entry when we programmatically set its state
    private boolean suppressSwitchListener = false;

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
        applyWindowInsets(view);
        setupViewModel();
        setupClickListeners();

        viewModel.loadProfile();
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
        viewModel.getNavigateToEditEvent().observe(getViewLifecycleOwner(), shouldNavigate -> {
            if (!Boolean.TRUE.equals(shouldNavigate)) return;
            viewModel.consumeNavigateToEdit();
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new EditProfileFragment(), EditProfileFragment.TAG)
                    .addToBackStack(null)
                    .commit();
        });

        viewModel.getNavigateToHistoryEvent().observe(getViewLifecycleOwner(), shouldNavigate -> {
            if (!Boolean.TRUE.equals(shouldNavigate)) return;
            viewModel.consumeNavigateToHistory();
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_profile_to_sessionHistoryFragment);
        });

        viewModel.getNavigateToFriendsEvent().observe(getViewLifecycleOwner(), shouldNavigate -> {
            if (!Boolean.TRUE.equals(shouldNavigate)) return;
            viewModel.consumeNavigateToFriends();
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_profile_to_friendsFragment);
        });

        viewModel.getNavigateToBlockedUsersEvent().observe(getViewLifecycleOwner(), shouldNavigate -> {
            if (!Boolean.TRUE.equals(shouldNavigate)) return;
            viewModel.consumeNavigateToBlockedUsers();
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_profile_to_blockedUsersFragment);
        });

        viewModel.getNavigateToLeaderboardEvent().observe(getViewLifecycleOwner(), shouldNavigate -> {
            if (!Boolean.TRUE.equals(shouldNavigate)) return;
            viewModel.consumeNavigateToLeaderboard();
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_profile_to_leaderboardFragment);
        });
    }

    // ── Setup helpers ─────────────────────────────────────────────────────────

    private void applyWindowInsets(View root) {
        profileContentRoot = root.findViewById(R.id.profileContentRoot);
        View bottomSpacer = root.findViewById(R.id.bottomSpacer);

        // Set spacer to 1/4 screen height once the view is laid out
        int screenHeight = root.getResources().getDisplayMetrics().heightPixels;
        android.view.ViewGroup.LayoutParams lp = bottomSpacer.getLayoutParams();
        lp.height = screenHeight / 4;
        bottomSpacer.setLayoutParams(lp);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            int bottomNavHeightPx = (int) (56 * root.getResources().getDisplayMetrics().density);
            profileContentRoot.setPadding(
                    profileContentRoot.getPaddingLeft(),
                    profileContentRoot.getPaddingTop(),
                    profileContentRoot.getPaddingRight(),
                    bottomNavHeightPx + bottomInset);
            return insets;
        });
    }

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

        btnEditProfile  = root.findViewById(R.id.btnEditProfile);

        menuWalkHistory  = root.findViewById(R.id.menuWalkHistory);
        menuMyBadges     = root.findViewById(R.id.menuMyBadges);
        menuLeaderboard  = root.findViewById(R.id.menuLeaderboard);
        menuSettings     = root.findViewById(R.id.menuSettings);
        menuFriends      = root.findViewById(R.id.menuFriends);
        menuBlockedUsers = root.findViewById(R.id.menuBlockedUsers);
        switchVisibility = root.findViewById(R.id.switchVisibility);
        btnLogoutAll     = root.findViewById(R.id.btnLogoutAll);
    }

    private void setupViewModel() {
        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        ProfileViewModelFactory factory =
                new ProfileViewModelFactory(app.getUserProfileRepository(), 
                                            requireContext(),
                                            app.getGamificationRepository(),
                                            app.getReviewRepository());
        viewModel = new ViewModelProvider(this, factory).get(ProfileViewModel.class);
    }

    private void setupClickListeners() {
        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v -> viewModel.onEditProfileClicked());
        }
        menuWalkHistory.setOnClickListener(v -> viewModel.onWalkHistoryClicked());
        menuMyBadges.setOnClickListener(v -> viewModel.onMyBadgesClicked());
        menuLeaderboard.setOnClickListener(v -> viewModel.onLeaderboardClicked());
        menuSettings.setOnClickListener(v -> viewModel.onSettingsClicked());
        menuFriends.setOnClickListener(v -> viewModel.onFriendsClicked());
        menuBlockedUsers.setOnClickListener(v -> viewModel.onBlockedUsersClicked());

        switchVisibility.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitchListener) return;
            VisibilityMode mode = isChecked ? VisibilityMode.PUBLIC : VisibilityMode.PRIVATE;
            viewModel.setVisibility(mode);
        });

        btnLogoutAll.setOnClickListener(v -> showLogoutAllConfirmation());
    }

    // ── State rendering ───────────────────────────────────────────────────────

    /**
     * Single source of truth for all View mutations.
     * Called every time the LiveData emits a new ProfileUiState.
     */
    private void renderState(ProfileUiState state) {
        if (state.isLoading()) {
            return;
        }

        if (state.getError() != null) {
            Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_SHORT).show();
            return;
        }

        // ── Avatar ──
        GlideHelper.loadCircle(imgProfileAvatar, state.getAvatarUrl());

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

        // ── Visibility switch ──
        if (state.getVisibilityMode() != null) {
            suppressSwitchListener = true;
            switchVisibility.setChecked(state.getVisibilityMode() == VisibilityMode.PUBLIC);
            suppressSwitchListener = false;
        }
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
     *
     * Badge labels are plain strings from the backend (e.g. "First Walk").
     * Icon drawables are resource IDs; 0 means no specific asset yet — the slot
     * keeps whatever placeholder the layout XML defines.
     */
    private void renderBadges(List<ProfileUiState.Badge> badges) {
        ImageView[] iconSlots  = {imgBadge1, imgBadge2, imgBadge3};
        TextView[]  labelSlots = {lblBadge1, lblBadge2, lblBadge3};

        for (int i = 0; i < iconSlots.length; i++) {
            View parent = (View) iconSlots[i].getParent();
            if (badges != null && i < badges.size()) {
                ProfileUiState.Badge badge = badges.get(i);
                labelSlots[i].setText(badge.label);
                if (badge.iconDrawableResId != 0) {
                    iconSlots[i].setImageResource(badge.iconDrawableResId);
                }
                parent.setVisibility(View.VISIBLE);
            } else {
                parent.setVisibility(View.GONE);
            }
        }
    }

    private void showLogoutAllConfirmation() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.profile_logout_all_confirm_title)
                .setMessage(R.string.profile_logout_all_confirm_message)
                .setPositiveButton(R.string.profile_logout_all_confirm_yes,
                        (dialog, which) -> viewModel.logoutAll())
                .setNegativeButton(R.string.profile_logout_all_confirm_no, null)
                .show();
    }
}
