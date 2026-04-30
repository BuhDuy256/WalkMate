package com.walkmate.ui.review;

import android.os.Bundle;
import android.view.ContextThemeWrapper;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.util.WindowInsetUtils;
import com.walkmate.domain.review.ReviewTag;

import java.util.ArrayList;
import java.util.List;

/**
 * Submit Review screen — standalone full-page Fragment.
 *
 * UX flow:
 *   1. Screen opens with 5 stars pre-selected and POSITIVE tags visible immediately.
 *   2. Tags populate automatically as soon as the API responds (non-blocking).
 *   3. Switching to 1–3 stars swaps the chip set to NEGATIVE tags with label
 *      "What could be improved?"; 4–5 stars restore POSITIVE tags.
 *   4. Tapping Submit gathers stars + checked tag IDs + comment and calls the VM.
 *   5. On success the button transitions to a green "submitted" state for 900 ms,
 *      then the screen pops back.
 */
public class SubmitReviewFragment extends Fragment {

    public static final String TAG              = "SubmitReviewFragment";
    public static final String ARG_SESSION_ID   = "SESSION_ID";
    public static final String ARG_PARTNER_NAME = "PARTNER_NAME";

    public static SubmitReviewFragment newInstance(String sessionId) {
        return newInstance(sessionId, null);
    }

    public static SubmitReviewFragment newInstance(String sessionId, @Nullable String partnerName) {
        SubmitReviewFragment f = new SubmitReviewFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID, sessionId);
        if (partnerName != null) args.putString(ARG_PARTNER_NAME, partnerName);
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

        WindowInsetUtils.applyStatusBarPadding(view.findViewById(R.id.subPageHeader));

        ratingBar          = view.findViewById(R.id.ratingBarReview);
        layoutTagSection   = view.findViewById(R.id.layoutTagSection);
        txtTagSectionLabel = view.findViewById(R.id.txtTagSectionLabel);
        chipGroupTags      = view.findViewById(R.id.chipGroupReviewTags);
        etComment          = view.findViewById(R.id.etReviewComment);
        btnSubmit          = view.findViewById(R.id.btnSubmitReview);
        txtAlreadyReviewed = view.findViewById(R.id.txtAlreadyReviewed);
        btnBack            = view.findViewById(R.id.btnSubPageBack);
        ((TextView) view.findViewById(R.id.txtSubPageTitle)).setText("Leave a Review");

        btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        // Set comment placeholder with partner name if provided
        Bundle args = getArguments();
        String sessionId    = args != null ? args.getString(ARG_SESSION_ID)   : null;
        String partnerName  = args != null ? args.getString(ARG_PARTNER_NAME) : null;
        if (partnerName == null) partnerName = "your walk partner";
        etComment.setHint("Tell others about walking with " + partnerName + "...");

        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        viewModel = new ViewModelProvider(this,
                new ReviewViewModelFactory(
                        app.getReviewRepository(),
                        app.getWalkSessionRepository()))
                .get(ReviewViewModel.class);

        // ── Tags arrive async: populate chips as soon as they're ready ─────────
        viewModel.getReviewUiState().observe(getViewLifecycleOwner(), state -> {
            int currentStars = (int) ratingBar.getRating();
            if (!state.availableTags.isEmpty()) {
                // Use current rating (default 5 = positive) to decide which chips to show
                populateChips(state.availableTags, currentStars > 0 ? currentStars : 5);
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

        // ── Submit lifecycle ───────────────────────────────────────────────────
        viewModel.getSubmitState().observe(getViewLifecycleOwner(), submitState -> {
            switch (submitState) {
                case LOADING:
                    btnSubmit.setEnabled(false);
                    break;
                case SUCCESS:
                    viewModel.getReviewUiState().removeObservers(getViewLifecycleOwner());
                    btnSubmit.setBackground(
                            ContextCompat.getDrawable(requireContext(), R.drawable.bg_btn_submit_success));
                    btnSubmit.setText(R.string.review_submitted_btn);
                    btnSubmit.setEnabled(false);
                    view.postDelayed(() -> {
                        if (isAdded()) {
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }, 900);
                    break;
                case ERROR:
                    btnSubmit.setEnabled(true);
                    String errMsg = viewModel.getError().getValue();
                    if (errMsg != null) {
                        Toast.makeText(requireContext(), errMsg, Toast.LENGTH_SHORT).show();
                    }
                    break;
                default:
                    break;
            }
        });

        // ── Rating change: swap chip set based on star polarity ────────────────
        ratingBar.setOnRatingBarChangeListener((bar, rating, fromUser) -> {
            if (!fromUser) return;
            int stars = (int) rating;
            if (stars == 0) return;
            ReviewUiState state = viewModel.getReviewUiState().getValue();
            List<ReviewTag> tags = (state != null) ? state.availableTags : null;
            if (tags != null && !tags.isEmpty()) {
                populateChips(tags, stars);
            } else {
                // Tags not loaded yet — just update the section label
                txtTagSectionLabel.setText(stars >= 4
                        ? "What went well?"
                        : "What could be improved?");
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
     * Shows POSITIVE tags for 4–5 stars; NEGATIVE for 1–3 stars.
     * Each chip uses the purple review style and clears prior selection on rebuild.
     */
    private void populateChips(List<ReviewTag> allTags, int stars) {
        chipGroupTags.removeAllViews();
        boolean showPositive = (stars >= 4);

        for (ReviewTag tag : allTags) {
            if (tag.isPositive() != showPositive) continue;

            Chip chip = new Chip(
                    new ContextThemeWrapper(requireContext(), R.style.Widget_WalkMate_Chip_Review));
            chip.setText(tag.getTagName());
            chip.setTag(tag.getTagId());
            chip.setCheckable(true);
            chip.setChecked(false);
            chipGroupTags.addView(chip);
        }

        txtTagSectionLabel.setText(showPositive ? "What went well?" : "What could be improved?");
        layoutTagSection.setVisibility(View.VISIBLE);
    }

    /** Returns the tag IDs of all checked chips. */
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
