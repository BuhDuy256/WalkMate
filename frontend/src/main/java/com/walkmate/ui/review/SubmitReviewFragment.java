package com.walkmate.ui.review;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.domain.review.ReviewTag;

import java.util.ArrayList;
import java.util.List;

/**
 * Submit Review screen — standalone full-page Fragment.
 *
 * UX flow:
 *   1. User arrives; star bar and transparency notice are immediately visible.
 *   2. As soon as the user taps a star, the structured-feedback chip group slides
 *      into view — POSITIVE tags for 4–5 stars, NEGATIVE for 1–3 stars.
 *   3. User optionally adds a free-text comment.
 *   4. Tapping Submit gathers stars + selected tag IDs + comment and calls the VM.
 */
public class SubmitReviewFragment extends Fragment {

    public static final String TAG            = "SubmitReviewFragment";
    public static final String ARG_SESSION_ID = "SESSION_ID";

    public static SubmitReviewFragment newInstance(String sessionId) {
        SubmitReviewFragment f = new SubmitReviewFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID, sessionId);
        f.setArguments(args);
        return f;
    }

    // ── Views ─────────────────────────────────────────────────────────────────

    private RatingBar    ratingBar;
    private LinearLayout layoutTagSection;
    private TextView     txtTagSectionLabel;
    private ChipGroup    chipGroupTags;
    private EditText     etComment;
    private Button       btnSubmit;
    private TextView     txtAlreadyReviewed;
    private View         btnBack;

    // ── State ─────────────────────────────────────────────────────────────────

    private ReviewViewModel viewModel;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_submit_review, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ratingBar          = view.findViewById(R.id.ratingBarReview);
        layoutTagSection   = view.findViewById(R.id.layoutTagSection);
        txtTagSectionLabel = view.findViewById(R.id.txtTagSectionLabel);
        chipGroupTags      = view.findViewById(R.id.chipGroupReviewTags);
        etComment          = view.findViewById(R.id.etReviewComment);
        btnSubmit          = view.findViewById(R.id.btnSubmitReview);
        txtAlreadyReviewed = view.findViewById(R.id.txtAlreadyReviewed);
        btnBack            = view.findViewById(R.id.btnBackReview);

        btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        String sessionId = getArguments() != null
                ? getArguments().getString(ARG_SESSION_ID) : null;

        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        viewModel = new ViewModelProvider(this,
                new ReviewViewModelFactory(
                        app.getReviewRepository(),
                        app.getWalkSessionRepository()))
                .get(ReviewViewModel.class);

        // ── Observe review state (already-reviewed / loading / error) ──────────
        viewModel.getReviewUiState().observe(getViewLifecycleOwner(), state -> {
            // Repopulate chips whenever the tag list updates (may arrive async).
            int currentRating = (int) ratingBar.getRating();
            if (!state.availableTags.isEmpty() && currentRating > 0) {
                populateChips(state.availableTags, currentRating);
            }

            switch (state.kind) {
                case IDLE:
                    btnSubmit.setEnabled(true);
                    txtAlreadyReviewed.setVisibility(View.GONE);
                    break;
                case LOADING:
                    btnSubmit.setEnabled(false);
                    break;
                case ALREADY_REVIEWED:
                    btnSubmit.setEnabled(false);
                    ratingBar.setIsIndicator(true);
                    etComment.setEnabled(false);
                    layoutTagSection.setVisibility(View.GONE);
                    txtAlreadyReviewed.setVisibility(View.VISIBLE);
                    break;
                case ERROR:
                    btnSubmit.setEnabled(true);
                    Toast.makeText(requireContext(), state.error, Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
        });

        // ── Observe submit lifecycle ───────────────────────────────────────────
        viewModel.getSubmitState().observe(getViewLifecycleOwner(), submitState -> {
            switch (submitState) {
                case LOADING:
                    btnSubmit.setEnabled(false);
                    break;
                case SUCCESS:
                    viewModel.getReviewUiState().removeObservers(getViewLifecycleOwner());
                    Toast.makeText(requireContext(), "Review submitted!", Toast.LENGTH_SHORT).show();
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                    break;
                case ERROR:
                    btnSubmit.setEnabled(true);
                    break;
                default:
                    break;
            }
        });

        // ── Rating → chip group: show chips only after the user interacts ──────
        ratingBar.setOnRatingBarChangeListener((bar, rating, fromUser) -> {
            if (!fromUser) return;
            int stars = (int) rating;
            if (stars == 0) {
                layoutTagSection.setVisibility(View.GONE);
                return;
            }
            ReviewUiState state = viewModel.getReviewUiState().getValue();
            List<ReviewTag> tags = (state != null) ? state.availableTags : null;
            if (tags != null && !tags.isEmpty()) {
                populateChips(tags, stars);
                layoutTagSection.setVisibility(View.VISIBLE);
            }
        });

        // ── Submit ────────────────────────────────────────────────────────────
        btnSubmit.setOnClickListener(v -> {
            if (sessionId == null) return;
            int stars      = (int) ratingBar.getRating();
            String comment = etComment.getText().toString().trim();
            List<String> tagIds = collectSelectedTagIds();
            viewModel.submitReview(sessionId, stars,
                    comment.isEmpty() ? null : comment, tagIds);
        });

        if (sessionId != null) {
            viewModel.loadReviewState(sessionId);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Rebuilds the chip group for the given star rating.
     * Shows POSITIVE tags for 4–5 stars; NEGATIVE tags for 1–3 stars.
     * Clears any previous chip selection to avoid stale state when the user
     * changes the rating before submitting.
     */
    private void populateChips(List<ReviewTag> allTags, int stars) {
        chipGroupTags.removeAllViews();
        boolean showPositive = (stars >= 4);

        for (ReviewTag tag : allTags) {
            if (tag.isPositive() != showPositive) continue;

            Chip chip = new Chip(requireContext());
            chip.setText(tag.getTagName());
            chip.setTag(tag.getTagId());        // store tagId for retrieval on submit
            chip.setCheckable(true);
            chip.setChecked(false);
            chip.setChipBackgroundColorResource(R.color.bg_warm_light);
            chip.setTextColor(requireContext().getColor(R.color.text_dark));
            chipGroupTags.addView(chip);
        }

        String label = showPositive
                ? "What went well?"
                : "What could be improved?";
        txtTagSectionLabel.setText(label);
    }

    /** Returns the tag IDs of all checked chips. Empty list if none are selected. */
    private List<String> collectSelectedTagIds() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < chipGroupTags.getChildCount(); i++) {
            View child = chipGroupTags.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                Object tag = child.getTag();
                if (tag instanceof String) {
                    ids.add((String) tag);
                }
            }
        }
        return ids;
    }
}
