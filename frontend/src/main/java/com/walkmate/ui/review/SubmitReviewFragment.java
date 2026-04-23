package com.walkmate.ui.review;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.R;
import com.walkmate.WalkMateApplication;

/**
 * Submit Review screen — standalone full-page Fragment.
 *
 * Launched from the Session History card when:
 *   GlobalStatus == COMPLETED AND both participants' status == COMPLETED.
 *
 * If the session has already been reviewed the form is disabled and a message
 * is shown. On successful submission pops back to Session History.
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

    private RatingBar ratingBar;
    private EditText  etComment;
    private Button    btnSubmit;
    private TextView  txtAlreadyReviewed;
    private View      btnBack;

    // ── MVVM ──────────────────────────────────────────────────────────────────

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

        viewModel.getReviewUiState().observe(getViewLifecycleOwner(), state -> {
            switch (state.kind) {
                case IDLE:
                    btnSubmit.setEnabled(true);
                    txtAlreadyReviewed.setVisibility(View.GONE);
                    break;
                case LOADING:
                    btnSubmit.setEnabled(false);
                    break;
                case SUCCESS:
                    Toast.makeText(requireContext(), "Review submitted!", Toast.LENGTH_SHORT).show();
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                    break;
                case ALREADY_REVIEWED:
                    btnSubmit.setEnabled(false);
                    ratingBar.setIsIndicator(true);
                    etComment.setEnabled(false);
                    txtAlreadyReviewed.setVisibility(View.VISIBLE);
                    break;
                case ERROR:
                    btnSubmit.setEnabled(true);
                    Toast.makeText(requireContext(), state.error, Toast.LENGTH_SHORT).show();
                    break;
            }
        });

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

        btnSubmit.setOnClickListener(v -> {
            if (sessionId == null) return;
            int stars      = (int) ratingBar.getRating();
            String comment = etComment.getText().toString().trim();
            viewModel.submitReview(sessionId, stars, comment.isEmpty() ? null : comment);
        });

        if (sessionId != null) {
            viewModel.loadReviewState(sessionId);
        }
    }
}
