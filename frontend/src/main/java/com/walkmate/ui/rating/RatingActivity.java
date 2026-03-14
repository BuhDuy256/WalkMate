package com.walkmate.ui.rating;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.walkmate.R;

public class RatingActivity extends AppCompatActivity {

    private RatingViewModel viewModel;

    // Views
    private ImageButton btnBack;
    private TextView tvPartnerName, tvDate, tvMonth, tvDuration, tvDistance, tvSteps, tvCharCount, btnLater;
    private RatingBar ratingBar;
    private ChipGroup chipGroupTags;
    private EditText etNote;
    private Button btnSubmitRating;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rating);

        viewModel = new ViewModelProvider(this).get(RatingViewModel.class);

        initViews();
        setupListeners();
        observeState();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvPartnerName = findViewById(R.id.tvPartnerName);
        tvDate = findViewById(R.id.tvDate);
        tvMonth = findViewById(R.id.tvMonth);
        tvDuration = findViewById(R.id.tvDuration);
        tvDistance = findViewById(R.id.tvDistance);
        tvSteps = findViewById(R.id.tvSteps);
        ratingBar = findViewById(R.id.ratingBar);
        chipGroupTags = findViewById(R.id.chipGroupTags);
        etNote = findViewById(R.id.etNote);
        tvCharCount = findViewById(R.id.tvCharCount);
        btnSubmitRating = findViewById(R.id.btnSubmitRating);
        btnLater = findViewById(R.id.btnLater);
        progressBar = findViewById(R.id.progressBar);

        // Create default chips
        String[] defaultTags = {"Friendly", "On-time", "Great chat", "Good pace", "Nature lover", "Safe route"};

        for (String tag : defaultTags) {
            Chip chip = new Chip(this);
            chip.setText(tag);
            chip.setCheckable(true);
            chip.setClickable(true);

            chip.setOnClickListener(v -> viewModel.toggleTag(tag));

            chipGroupTags.addView(chip);
        }
    }

    private void setupListeners() {

        btnBack.setOnClickListener(v -> finish());

        ratingBar.setOnRatingBarChangeListener((bar, rating, fromUser) -> {
            if (fromUser) {
                viewModel.updateRating((int) rating);
            }
        });

        etNote.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.updateNote(s.toString());
            }
        });

        btnSubmitRating.setOnClickListener(v -> viewModel.submitRating());

        btnLater.setOnClickListener(v -> finish());
    }

    private void observeState() {

        viewModel.getState().observe(this, state -> {

            // Loading
            progressBar.setVisibility(state.isLoading ? View.VISIBLE : View.GONE);
            btnSubmitRating.setEnabled(!state.isLoading);

            // Error
            if (state.errorMessage != null) {
                Toast.makeText(this, state.errorMessage, Toast.LENGTH_SHORT).show();
            }

            // Success
            if (state.isSubmitSuccessful) {
                Toast.makeText(this, "Rating Submitted successfully!", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            // Render content
            if (state.partnerName != null) {

                tvPartnerName.setText(state.partnerName);

                // Split date "04 MAR"
                if (state.walkDate != null) {
                    String[] parts = state.walkDate.split(" ");

                    if (parts.length == 2) {
                        tvDate.setText(parts[0]);
                        tvMonth.setText(parts[1]);
                    }
                }

                tvDuration.setText(state.duration);
                tvDistance.setText(state.distance);
                tvSteps.setText(state.steps);

                ratingBar.setRating(state.currentRating);

                if (state.note != null) {
                    tvCharCount.setText(state.note.length() + "/200");
                }
            }
        });
    }
}