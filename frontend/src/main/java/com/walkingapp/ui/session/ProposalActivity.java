package com.walkingapp.ui.session;

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

public class ProposalActivity extends AppCompatActivity {
  private SessionViewModel viewModel;

  private int proposalId;
  private int userId;

  private TextView tvProposalStatus;
  private TextView tvProposalDetails;
  private Button btnConfirm;
  private Button btnCancel;
  private Button btnViewSession;
  private ProgressBar progressBar;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_proposal);

    viewModel = new ViewModelProvider(this).get(SessionViewModel.class);

    proposalId = getIntent().getIntExtra("proposal_id", -1);
    userId = getIntent().getIntExtra("user_id", -1);

    initViews();
    setupObservers();
    setupListeners();

    loadProposal();
  }

  private void initViews() {
    tvProposalStatus = findViewById(R.id.tvProposalStatus);
    tvProposalDetails = findViewById(R.id.tvProposalDetails);
    btnConfirm = findViewById(R.id.btnConfirm);
    btnCancel = findViewById(R.id.btnCancel);
    btnViewSession = findViewById(R.id.btnViewSession);
    progressBar = findViewById(R.id.progressBar);
  }

  private void setupObservers() {
    viewModel.getProposalResult().observe(this, result -> {
      if (result == null)
        return;

      switch (result.getStatus()) {
        case LOADING:
          showLoading(true);
          break;

        case SUCCESS:
          showLoading(false);
          displayProposal(result.getData());

          String status = result.getData().getStatus();
          if ("CONFIRMED".equals(status)) {
            Toast.makeText(this, "Proposal confirmed!", Toast.LENGTH_SHORT).show();
            btnViewSession.setVisibility(View.VISIBLE);
            btnConfirm.setEnabled(false);
            btnCancel.setEnabled(false);
          } else if ("CANCELLED".equals(status)) {
            Toast.makeText(this, "Proposal cancelled", Toast.LENGTH_SHORT).show();
            btnConfirm.setEnabled(false);
            btnCancel.setEnabled(false);
          }
          break;

        case ERROR:
          showLoading(false);
          Toast.makeText(this, result.getError(), Toast.LENGTH_LONG).show();
          break;
      }
    });
  }

  private void setupListeners() {
    btnConfirm.setOnClickListener(v -> {
      viewModel.confirmProposal(proposalId, userId);
    });

    btnCancel.setOnClickListener(v -> {
      viewModel.cancelProposal(proposalId, userId);
    });

    btnViewSession.setOnClickListener(v -> {
      Intent intent = new Intent(this, SessionActivity.class);
      intent.putExtra("user_id", userId);
      startActivity(intent);
    });
  }

  private void loadProposal() {
    viewModel.loadUserProposal(userId);
  }

  private void displayProposal(com.walkingapp.model.Proposal proposal) {
    tvProposalStatus.setText("Status: " + proposal.getStatus());

    StringBuilder details = new StringBuilder();
    details.append("Proposal ID: ").append(proposal.getId()).append("\n");
    details.append("Requester User: ").append(proposal.getRequesterUserId()).append("\n");
    details.append("Target User: ").append(proposal.getTargetUserId()).append("\n");
    details.append("Expires At: ").append(proposal.getExpiresAt()).append("\n");

    tvProposalDetails.setText(details.toString());
  }

  private void showLoading(boolean show) {
    progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    btnConfirm.setEnabled(!show);
    btnCancel.setEnabled(!show);
  }
}
