package com.walkmate.ui.rating;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.walkmate.R;
import com.walkmate.data.datasource.remote.api.RatingApiService;
import com.walkmate.data.mapper.RatingDomainToDtoMapper;
import com.walkmate.data.network.ApiClient;
import com.walkmate.data.repository.RatingRepositoryImpl;
import com.walkmate.domain.rating.RatingRepository;
import com.walkmate.domain.rating.RatingService;

import java.util.List;
import java.util.UUID;

public class RatingActivity extends AppCompatActivity {

    private static final int MAX_COMMENT_LENGTH = 200;

    private RatingViewModel viewModel;

    private ImageView btnBack;
    private TextView tvSubtitle;
    private TextView tvPartnerName;
    private TextView tvWalkStatus;
    private TextView tvDuration;
    private TextView tvDistance;
    private TextView tvSteps;
    private TextView tvWalkDate;
    private TextView tvRatingLabel;
    private TextView tvRatingSubtitle;
    private ImageView[] starViews;
    private ChipGroup chipGroupTags;
    private TextInputEditText etComment;
    private TextView tvCharCount;
    private MaterialButton btnSubmit;
    private ProgressBar progressBar;
    private TextView tvError;
    private TextView tvMaybeLater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rating);

        initViews();
        initViewModel();
        observeState();
        observeEffects();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back);
        tvSubtitle = findViewById(R.id.tv_subtitle);
        tvPartnerName = findViewById(R.id.tv_partner_name);
        tvWalkStatus = findViewById(R.id.tv_walk_status);
        tvDuration = findViewById(R.id.tv_duration);
        tvDistance = findViewById(R.id.tv_distance);
        tvSteps = findViewById(R.id.tv_steps);
        tvWalkDate = findViewById(R.id.tv_walk_date);
        tvRatingLabel = findViewById(R.id.tv_rating_label);
        tvRatingSubtitle = findViewById(R.id.tv_rating_subtitle);
        tvCharCount = findViewById(R.id.tv_char_count);

        starViews = new ImageView[]{
                findViewById(R.id.star_1),
                findViewById(R.id.star_2),
                findViewById(R.id.star_3),
                findViewById(R.id.star_4),
                findViewById(R.id.star_5)
        };

        chipGroupTags = findViewById(R.id.chip_group_tags);
        etComment = findViewById(R.id.et_comment);
        btnSubmit = findViewById(R.id.btn_submit);
        progressBar = findViewById(R.id.progress_bar);
        tvError = findViewById(R.id.tv_error);
        tvMaybeLater = findViewById(R.id.tv_maybe_later);

        setupBackButton();
        setupStarClickListeners();
        setupCommentListener();
        setupMaybeLaterListener();
    }

    private void setupBackButton() {
        btnBack.setOnClickListener(v -> finish());
    }

    private void setupCommentListener() {
        etComment.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.onEvent(new RatingUiEvent.CommentChanged(s.toString()));
                updateCharCount(s.length());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void updateCharCount(int length) {
        tvCharCount.setText(length + "/" + MAX_COMMENT_LENGTH);
    }

    private void setupMaybeLaterListener() {
        tvMaybeLater.setOnClickListener(v -> viewModel.onEvent(new RatingUiEvent.CancelClicked()));
    }
     
    private void initViewModel() {
        UUID sessionId = getIntent().hasExtra("SESSION_ID")
                ? UUID.fromString(getIntent().getStringExtra("SESSION_ID"))
                : UUID.randomUUID();
        UUID partnerId = getIntent().hasExtra("PARTNER_ID")
                ? UUID.fromString(getIntent().getStringExtra("PARTNER_ID"))
                : UUID.randomUUID();
        String partnerName = getIntent().getStringExtra("PARTNER_NAME");
        if (partnerName == null) {
            partnerName = "Sophia K.";
        }
        UUID currentUserId = getIntent().hasExtra("USER_ID")
                ? UUID.fromString(getIntent().getStringExtra("USER_ID"))
                : UUID.randomUUID();

        RatingViewData initialData = RatingViewData.createInitial(sessionId, partnerId, partnerName);

        RatingApiService apiService = ApiClient.getRatingApi();
        RatingDomainToDtoMapper mapper = new RatingDomainToDtoMapper();
        RatingRepository repository = new RatingRepositoryImpl(apiService, mapper);
        RatingService service = new RatingService(repository);

        RatingViewModelFactory factory = new RatingViewModelFactory(service, initialData, currentUserId);
        viewModel = new ViewModelProvider(this, factory).get(RatingViewModel.class);

        btnSubmit.setOnClickListener(v -> viewModel.onEvent(new RatingUiEvent.SubmitClicked()));
    }

    private void setupStarClickListeners() {
        for (int i = 0; i < starViews.length; i++) {
            final int starNumber = i + 1;
            starViews[i].setOnClickListener(v ->
                    viewModel.onEvent(new RatingUiEvent.StarSelected(starNumber))
            );
        }
    }

    private void observeState() {
        viewModel.uiState.observe(this, this::renderState);
    }

    private void observeEffects() {
        viewModel.uiEffect.observe(this, this::handleEffect);
    }

    private void renderState(RatingUiState state) {
        progressBar.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);
        btnSubmit.setEnabled(state.isSubmitEnabled() && !state.isLoading());

        RatingViewData data = state.getData();

        tvPartnerName.setText(data.getPartner().getName());
        tvWalkStatus.setText("Walk Completed");
        tvDuration.setText(data.getStats().getDuration());
        tvDistance.setText(data.getStats().getDistance());
        tvSteps.setText(data.getStats().getSteps());
        tvWalkDate.setText(data.getStats().getWalkDate());

        renderStars(data.getSelectedStars());
        updateRatingLabel(data.getSelectedStars());
        renderTags(data.getAvailableTags());

        if (!etComment.getText().toString().equals(data.getComment())) {
            etComment.setText(data.getComment());
        }

        if (state.getError() != null) {
            tvError.setText(state.getError());
            tvError.setVisibility(View.VISIBLE);
        } else {
            tvError.setVisibility(View.GONE);
        }
    }

    private void updateRatingLabel(int stars) {
        String label;
        String subtitle;
        switch (stars) {
            case 1:
                label = "Not great";
                subtitle = "We're sorry to hear that";
                break;
            case 2:
                label = "Could be better";
                subtitle = "Thanks for your feedback";
                break;
            case 3:
                label = "It was okay";
                subtitle = "Room for improvement";
                break;
            case 4:
                label = "Great time!";
                subtitle = "You had a solid walk";
                break;
            case 5:
                label = "Amazing!";
                subtitle = "What an incredible walk";
                break;
            default:
                label = "How was it?";
                subtitle = "Tap to rate your experience";
        }
        tvRatingLabel.setText(label);
        tvRatingSubtitle.setText(subtitle);
    }

    private void renderStars(int selectedStars) {
        for (int i = 0; i < starViews.length; i++) {
            if (i < selectedStars) {
                starViews[i].setImageResource(R.drawable.ic_star_filled);
            } else {
                starViews[i].setImageResource(R.drawable.ic_star_outline);
            }
        }
    }

    private void renderTags(List<RatingViewData.TagViewData> tags) {
        chipGroupTags.removeAllViews();

        for (RatingViewData.TagViewData tag : tags) {
            Chip chip = new Chip(this);
            chip.setText(tag.getDisplayText());
            chip.setCheckable(true);
            chip.setChecked(tag.isSelected());
            chip.setChipBackgroundColorResource(
                    tag.isSelected() ? R.color.chip_selected_bg : R.color.chip_unselected_bg
            );
            chip.setTextColor(getResources().getColor(
                    tag.isSelected() ? R.color.chip_selected_text : R.color.chip_unselected_text, null
            ));
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                viewModel.onEvent(new RatingUiEvent.TagToggled(tag.getCode()));
            });
            chipGroupTags.addView(chip);
        }
    }

    private void handleEffect(RatingUiEffect effect) {
        if (effect instanceof RatingUiEffect.NavigateToSuccess) {
            Toast.makeText(this, "Rating submitted successfully!", Toast.LENGTH_SHORT).show();
            finish();
        } else if (effect instanceof RatingUiEffect.NavigateBack) {
            finish();
        } else if (effect instanceof RatingUiEffect.ShowToast) {
            Toast.makeText(this, ((RatingUiEffect.ShowToast) effect).message(), Toast.LENGTH_SHORT).show();
        }
    }
}
