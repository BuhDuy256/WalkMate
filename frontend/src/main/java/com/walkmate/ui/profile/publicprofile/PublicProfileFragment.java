package com.walkmate.ui.profile.publicprofile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.designsystem.view.AvatarInitialView;
import com.walkmate.core.designsystem.view.TagChipGroup;
import com.walkmate.core.designsystem.view.WalkMateButton;
import com.walkmate.core.designsystem.view.WalkMateStatColumn;
import com.walkmate.domain.gamification.UserStats;
import com.walkmate.domain.review.WalkReview;
import com.walkmate.domain.social.UserSummary;
import com.walkmate.ui.auth.AuthActivity;
import com.walkmate.ui.profile.ProfileUiState.Badge;

import java.util.List;
import java.util.Locale;

/**
 * Public profile screen.
 *
 * Displays any user's public-facing profile and exposes contextual
 * friendship actions. The local user's own profile suppresses all
 * friendship action views (isSelf == true).
 *
 * Entry points:
 *   - HomeFragment (quick-invite card tap) via NavController
 *   - ProposalFragment (partner avatar/name tap) via NavController
 *   - SessionHistoryFragment (partner name tap) via NavController
 *   - PostSessionSummaryFragment (partner name tap) via MainActivity Intent → NavController
 *
 * Navigation argument:
 *   Bundle key "userId" — String, required.
 */
public class PublicProfileFragment extends Fragment {

    public static final String TAG                    = "PublicProfileFragment";
    public static final String ARG_USER_ID            = "userId";
    public static final String ARG_VIEW_MODE          = "viewMode";
    public static final String ARG_ALLOW_FRIEND_REQUEST = "allowFriendRequest";

    // ── Views ─────────────────────────────────────────────────────────────────

    private ProgressBar     progressBar;
    private TextView        txtError;
    private View            contentView;

    private AvatarInitialView   avatarView;
    private TextView            txtName;
    private TextView            txtBio;
    private TagChipGroup        chipGroupTags;

    private WalkMateStatColumn  statDistance;
    private WalkMateStatColumn  statSessions;
    private WalkMateStatColumn  statTrustScore;

    private ChipGroup           chipGroupBadges;
    private TextView            txtNoBadges;

    private RecyclerView        rvReviews;
    private TextView            txtNoReviews;
    private ReviewAdapter       reviewAdapter;

    private View                layoutFriendshipActions;
    private WalkMateButton      btnAddFriend;
    private WalkMateButton      btnRequestSent;
    private WalkMateButton      btnAcceptRequest;
    private WalkMateButton      btnDeclineRequest;
    private WalkMateButton      btnInviteWalk;
    private WalkMateButton      btnRemoveFriend;

    private View                btnOverflowMenu;
    private View                btnBack;

    // ── Friend-only extras ────────────────────────────────────────────────────

    private View     layoutLastActiveAt;
    private TextView txtLastActiveAt;

    // ── MVVM ──────────────────────────────────────────────────────────────────

    private PublicProfileViewModel viewModel;

    // Cached for friendship mutation callbacks
    private String  currentUserId;
    // requestId when PENDING_RECEIVED — populated from the profile state
    private String  pendingRequestId;
    // false when the caller does not allow friend requests (e.g. Proposal screen)
    private boolean allowFriendRequest = true;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_public_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupRecyclerView();
        setupViewModel();

        Bundle args = getArguments();
        currentUserId      = args != null ? args.getString(ARG_USER_ID) : null;
        allowFriendRequest = args == null || args.getBoolean(ARG_ALLOW_FRIEND_REQUEST, true);

        setupClickListeners(view);

        if (currentUserId != null) {
            viewModel.loadProfile(currentUserId);
        }

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
        viewModel.getNavigateBackEvent().observe(getViewLifecycleOwner(), shouldGoBack -> {
            if (!Boolean.TRUE.equals(shouldGoBack)) return;
            viewModel.consumeNavigateBackEvent();
            navigateBack();
        });
    }

    // ── Setup helpers ─────────────────────────────────────────────────────────

    private void bindViews(View root) {
        progressBar     = root.findViewById(R.id.progressPublicProfile);
        txtError        = root.findViewById(R.id.txtPublicProfileError);
        contentView     = root.findViewById(R.id.contentPublicProfile);

        avatarView      = root.findViewById(R.id.avatarPublicProfile);
        txtName         = root.findViewById(R.id.txtPublicProfileName);
        txtBio          = root.findViewById(R.id.txtPublicProfileBio);
        chipGroupTags   = root.findViewById(R.id.chipGroupPublicProfileTags);

        statDistance    = root.findViewById(R.id.statPublicDistance);
        statSessions    = root.findViewById(R.id.statPublicSessions);
        statTrustScore  = root.findViewById(R.id.statPublicTrustScore);

        chipGroupBadges = root.findViewById(R.id.chipGroupPublicBadges);
        txtNoBadges     = root.findViewById(R.id.txtNoBadges);

        rvReviews       = root.findViewById(R.id.rvPublicProfileReviews);
        txtNoReviews    = root.findViewById(R.id.txtNoReviews);

        layoutLastActiveAt = root.findViewById(R.id.layoutLastActiveAt);
        txtLastActiveAt    = root.findViewById(R.id.txtLastActiveAt);

        layoutFriendshipActions = root.findViewById(R.id.layoutFriendshipActions);
        btnAddFriend            = root.findViewById(R.id.btnAddFriend);
        btnRequestSent          = root.findViewById(R.id.btnRequestSent);
        btnAcceptRequest        = root.findViewById(R.id.btnAcceptRequest);
        btnDeclineRequest       = root.findViewById(R.id.btnDeclineRequest);
        btnInviteWalk           = root.findViewById(R.id.btnInviteWalk);
        btnRemoveFriend         = root.findViewById(R.id.btnRemoveFriend);

        btnOverflowMenu = root.findViewById(R.id.btnOverflowMenu);
        btnBack         = root.findViewById(R.id.btnBackPublicProfile);
    }

    private void setupRecyclerView() {
        reviewAdapter = new ReviewAdapter();
        rvReviews.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvReviews.setAdapter(reviewAdapter);
        rvReviews.setNestedScrollingEnabled(false);
    }

    private void setupViewModel() {
        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        PublicProfileViewModelFactory factory = new PublicProfileViewModelFactory(
                app.getSocialRepository(),
                app.getGamificationRepository(),
                app.getReviewRepository(),
                app.getSessionManager().getUserId());
        viewModel = new ViewModelProvider(this, factory).get(PublicProfileViewModel.class);
    }

    private void setupClickListeners(View root) {
        btnBack.setOnClickListener(v -> navigateBack());

        btnOverflowMenu.setOnClickListener(v -> showOverflowMenu(v));

        btnAddFriend.setOnClickListener(v -> {
            if (!requiresAuth()) return;
            viewModel.sendFriendRequest(currentUserId);
        });

        btnAcceptRequest.setOnClickListener(v -> {
            if (!requiresAuth()) return;
            if (pendingRequestId != null) {
                viewModel.acceptIncomingRequest(pendingRequestId);
            }
        });

        btnDeclineRequest.setOnClickListener(v -> {
            if (!requiresAuth()) return;
            if (pendingRequestId != null) {
                viewModel.declineIncomingRequest(pendingRequestId);
            }
        });

        btnRemoveFriend.setOnClickListener(v -> {
            if (!requiresAuth()) return;
            viewModel.removeFriend(currentUserId);
        });

        btnInviteWalk.setOnClickListener(v -> {
            if (!requiresAuth()) return;
            // Phase 5 will deep-link to ExploreFragment with friendId pre-filled.
            Toast.makeText(requireContext(), "Invite Walk — coming soon!", Toast.LENGTH_SHORT).show();
        });
    }

    // ── State rendering ───────────────────────────────────────────────────────

    private void renderState(PublicProfileUiState state) {
        if (state.isLoading()) {
            progressBar.setVisibility(View.VISIBLE);
            contentView.setVisibility(View.GONE);
            txtError.setVisibility(View.GONE);
            return;
        }

        progressBar.setVisibility(View.GONE);

        if (state.getError() != null) {
            txtError.setVisibility(View.VISIBLE);
            txtError.setText(state.getError());
            contentView.setVisibility(View.GONE);
            return;
        }

        txtError.setVisibility(View.GONE);
        contentView.setVisibility(View.VISIBLE);

        renderIdentity(state.getProfile());
        renderFriendExtras(state.getProfile(), state.isFriend());
        renderStats(state.getStats(), state.getProfile());
        renderBadges(state.getBadges());
        renderReviews(state.getReviews());
        renderFriendshipActions(state.getProfile(), state.getFriendshipStatus(), state.isSelf());

        // Overflow menu visible only when viewing someone else's profile
        btnOverflowMenu.setVisibility(state.isSelf() ? View.GONE : View.VISIBLE);
    }

    private void renderIdentity(UserSummary profile) {
        if (profile == null) return;

        avatarView.bind(profile.getFullName(), profile.getAvatarUrl());
        txtName.setText(profile.getFullName());

        // Bio
        String bio = profile.getBio();
        if (bio != null && !bio.isEmpty()) {
            txtBio.setText(bio);
            txtBio.setVisibility(View.VISIBLE);
        } else {
            txtBio.setVisibility(View.GONE);
        }

        // Tags
        List<String> tags = profile.getTags();
        if (tags != null && !tags.isEmpty()) {
            chipGroupTags.removeAllViews();
            for (String tag : tags) {
                com.google.android.material.chip.Chip chip =
                        new com.google.android.material.chip.Chip(requireContext());
                chip.setText(tag);
                chip.setClickable(false);
                chipGroupTags.addView(chip);
            }
            chipGroupTags.setVisibility(View.VISIBLE);
        } else {
            chipGroupTags.setVisibility(View.GONE);
        }
    }

    private void renderStats(UserStats stats, UserSummary profile) {
        if (stats != null) {
            statDistance.bind(
                    String.format(Locale.getDefault(), "%.1f", stats.getTotalDistanceKm()),
                    "Total km");
            statSessions.bind(String.valueOf(stats.getCompletedSessions()), "Sessions");
            statTrustScore.bind(String.valueOf(stats.getTrustScore()), "Trust Score");
        } else {
            statDistance.bind("—", "Total km");
            statSessions.bind("—", "Sessions");
            statTrustScore.bind("—", "Trust Score");
        }
    }

    private void renderBadges(List<Badge> badges) {
        chipGroupBadges.removeAllViews();
        if (badges == null || badges.isEmpty()) {
            chipGroupBadges.setVisibility(View.GONE);
            txtNoBadges.setVisibility(View.VISIBLE);
            return;
        }

        chipGroupBadges.setVisibility(View.VISIBLE);
        txtNoBadges.setVisibility(View.GONE);

        for (Badge badge : badges) {
            Chip chip = new Chip(requireContext());
            chip.setText(badge.label);
            chip.setClickable(false);
            chipGroupBadges.addView(chip);
        }
    }

    private void renderReviews(List<WalkReview> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            rvReviews.setVisibility(View.GONE);
            txtNoReviews.setVisibility(View.VISIBLE);
        } else {
            rvReviews.setVisibility(View.VISIBLE);
            txtNoReviews.setVisibility(View.GONE);
            reviewAdapter.submitList(reviews);
        }
    }

    private void renderFriendExtras(UserSummary profile, boolean isFriend) {
        if (layoutLastActiveAt == null) return;
        if (isFriend && profile != null && profile.getLastActiveAt() != null) {
            txtLastActiveAt.setText(profile.getLastActiveAt());
            layoutLastActiveAt.setVisibility(View.VISIBLE);
        } else {
            layoutLastActiveAt.setVisibility(View.GONE);
        }
    }

    private void renderFriendshipActions(UserSummary profile, String status, boolean isSelf) {
        // Hide all friendship views when viewing own profile or when caller disallows it
        boolean hide = isSelf || !allowFriendRequest;
        layoutFriendshipActions.setVisibility(hide ? View.GONE : View.VISIBLE);
        if (hide) return;

        // Reset all to gone, then show the relevant subset
        btnAddFriend.setVisibility(View.GONE);
        btnRequestSent.setVisibility(View.GONE);
        btnAcceptRequest.setVisibility(View.GONE);
        btnDeclineRequest.setVisibility(View.GONE);
        btnInviteWalk.setVisibility(View.GONE);
        btnRemoveFriend.setVisibility(View.GONE);

        if (status == null) status = "NONE";
        switch (status) {
            case "NONE":
                btnAddFriend.setVisibility(View.VISIBLE);
                break;
            case "PENDING_SENT":
                btnRequestSent.setVisibility(View.VISIBLE);
                break;
            case "PENDING_RECEIVED":
                // Populate pendingRequestId so accept/decline callbacks work
                pendingRequestId = (profile != null) ? profile.getPendingRequestId() : null;
                btnAcceptRequest.setVisibility(View.VISIBLE);
                btnDeclineRequest.setVisibility(View.VISIBLE);
                break;
            case "FRIENDS":
                btnInviteWalk.setVisibility(View.VISIBLE);
                btnRemoveFriend.setVisibility(View.VISIBLE);
                break;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final int MENU_BLOCK_USER = 1;

    private void showOverflowMenu(View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, MENU_BLOCK_USER, 0, "Block User");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_BLOCK_USER) {
                if (!requiresAuth()) return true;
                viewModel.blockUser(currentUserId);
                return true;
            }
            return false;
        });
        popup.show();
    }

    /**
     * Returns true if the user is authenticated.
     * If not, shows a toast and navigates to AuthActivity.
     */
    private boolean requiresAuth() {
        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        if (app.getSessionManager().hasUsableAccessToken()) return true;
        Toast.makeText(requireContext(),
                "Log in to manage friendships.", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(requireContext(), AuthActivity.class));
        return false;
    }

    private void navigateBack() {
        requireActivity().getOnBackPressedDispatcher().onBackPressed();
    }
}
