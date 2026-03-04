package com.walkingapp.ui.intent;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.walkingapp.R;
import com.walkingapp.ui.session.ProposalActivity;

/**
 * SIMPLIFIED Activity using refactored state machine ViewModel.
 * 
 * BEFORE: Two separate observers, manual state coordination, complex button
 * logic
 * AFTER: Single observer, deterministic rendering, simple button logic
 */
public class IntentActivitySimplified extends AppCompatActivity {

  private IntentViewModelRefactored viewModel;

  private Button btnFindMatch;
  private ProgressBar progressBar;
  private TextView tvStatus;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_intent);

    viewModel = new ViewModelProvider(this).get(IntentViewModelRefactored.class);

    initViews();
    setupObserver(); // SINGLE observer!
    setupListeners();
  }

  private void initViews() {
    btnFindMatch = findViewById(R.id.btnFindMatch);
    progressBar = findViewById(R.id.progressBar);
    tvStatus = findViewById(R.id.tvStatus);
  }

  /**
   * SINGLE OBSERVER - All UI updates in one place, deterministic rendering based
   * on state.
   * 
   * This is the ONLY difference from the old Activity - one observer instead of
   * two,
   * and simpler rendering logic.
   */
  private void setupObserver() {
    viewModel.getScreenState().observe(this, state -> {
      // Render UI deterministically based on exclusive state
      renderState(state);
    });
  }

  /**
   * Deterministic rendering function - given a state, render UI.
   * No need to track history, combine streams, or maintain local state.
   */
  private void renderState(IntentScreenState state) {
    if (state instanceof IntentScreenState.Idle) {
      renderIdle();

    } else if (state instanceof IntentScreenState.CreatingIntent) {
      renderCreatingIntent();

    } else if (state instanceof IntentScreenState.FindingMatch) {
      renderFindingMatch();

    } else if (state instanceof IntentScreenState.MatchFound) {
      renderMatchFound((IntentScreenState.MatchFound) state);

    } else if (state instanceof IntentScreenState.NoMatchFound) {
      renderNoMatchFound((IntentScreenState.NoMatchFound) state);

    } else if (state instanceof IntentScreenState.Error) {
      renderError((IntentScreenState.Error) state);
    }
  }

  private void renderIdle() {
    progressBar.setVisibility(View.GONE);
    btnFindMatch.setEnabled(true);
    tvStatus.setText("Điền thông tin và tìm partner");
  }

  private void renderCreatingIntent() {
    progressBar.setVisibility(View.VISIBLE);
    btnFindMatch.setEnabled(false);
    tvStatus.setText("Đang tạo intent...");
  }

  private void renderFindingMatch() {
    progressBar.setVisibility(View.VISIBLE);
    btnFindMatch.setEnabled(false);
    tvStatus.setText("Đang tìm partner phù hợp...");
  }

  private void renderMatchFound(IntentScreenState.MatchFound state) {
    progressBar.setVisibility(View.GONE);
    btnFindMatch.setEnabled(true); // Enable for next match
    tvStatus.setText("✓ GHÉP THÀNH CÔNG!");

    // Show success dialog
    new AlertDialog.Builder(this)
        .setTitle("🎉 GHÉP THÀNH CÔNG!")
        .setMessage("Đã tìm thấy partner phù hợp!\nProposal ID: " + state.getProposal().getId())
        .setPositiveButton("Xem Chi Tiết", (dialog, which) -> {
          Intent intent = new Intent(this, ProposalActivity.class);
          intent.putExtra("proposal_id", state.getProposal().getId());
          intent.putExtra("user_id", getUserId());
          startActivity(intent);
        })
        .setNegativeButton("OK", null)
        .setOnDismissListener(d -> viewModel.reset()) // Reset to Idle after dialog dismissed
        .show();
  }

  private void renderNoMatchFound(IntentScreenState.NoMatchFound state) {
    progressBar.setVisibility(View.GONE);
    btnFindMatch.setEnabled(true); // Enable to retry
    tvStatus.setText("Chưa tìm thấy partner phù hợp");
    Toast.makeText(this, state.getMessage(), Toast.LENGTH_LONG).show();

    // Auto-reset to Idle after showing message
    viewModel.reset();
  }

  private void renderError(IntentScreenState.Error state) {
    progressBar.setVisibility(View.GONE);
    btnFindMatch.setEnabled(true); // Enable to retry
    tvStatus.setText("Lỗi: " + state.getErrorMessage());
    Toast.makeText(this, "Lỗi: " + state.getErrorMessage(), Toast.LENGTH_LONG).show();

    // Auto-reset to Idle after showing error
    viewModel.reset();
  }

  private void setupListeners() {
    btnFindMatch.setOnClickListener(v -> {
      if (validateInputs()) {
        int userId = getUserId();
        String walkType = getWalkType();
        String startAt = getStartAt();
        int flexMinutes = getFlexMinutes();
        double lat = getLat();
        double lng = getLng();
        int radiusM = getRadiusM();

        // Single action call - ViewModel handles the entire workflow
        viewModel.createIntentAndFindMatch(userId, walkType, startAt, flexMinutes, lat, lng, radiusM);
      }
    });
  }

  // Helper methods (stub implementations)
  private boolean validateInputs() {
    return true;
  }

  private int getUserId() {
    return 1;
  }

  private String getWalkType() {
    return "casual";
  }

  private String getStartAt() {
    return "2026-03-04T18:00:00";
  }

  private int getFlexMinutes() {
    return 30;
  }

  private double getLat() {
    return 1.0;
  }

  private double getLng() {
    return 1.0;
  }

  private int getRadiusM() {
    return 1000;
  }
}
