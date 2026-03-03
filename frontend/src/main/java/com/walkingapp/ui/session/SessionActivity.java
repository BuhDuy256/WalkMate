package com.walkingapp.ui.session;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.walkingapp.R;
import com.walkingapp.model.Session;

public class SessionActivity extends AppCompatActivity {
  private SessionViewModel viewModel;

  private int userId;
  private Session currentSession;

  private TextView tvSessionStatus;
  private TextView tvSessionDetails;
  private Button btnStart;
  private Button btnComplete;
  private Button btnRefresh;
  private ProgressBar progressBar;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_session);

    viewModel = new ViewModelProvider(this).get(SessionViewModel.class);

    userId = getIntent().getIntExtra("user_id", -1);

    initViews();
    setupObservers();
    setupListeners();

    loadSession();
  }

  private void initViews() {
    tvSessionStatus = findViewById(R.id.tvSessionStatus);
    tvSessionDetails = findViewById(R.id.tvSessionDetails);
    btnStart = findViewById(R.id.btnStart);
    btnComplete = findViewById(R.id.btnComplete);
    btnRefresh = findViewById(R.id.btnRefresh);
    progressBar = findViewById(R.id.progressBar);
  }

  private void setupObservers() {
    viewModel.getSessionResult().observe(this, result -> {
      if (result == null)
        return;

      switch (result.getStatus()) {
        case LOADING:
          showLoading(true);
          break;

        case SUCCESS:
          showLoading(false);
          currentSession = result.getData();
          displaySession(currentSession);
          updateButtonStates(currentSession.getStatus());
          break;

        case ERROR:
          showLoading(false);
          tvSessionStatus.setText("Error: " + result.getError());
          Toast.makeText(this, result.getError(), Toast.LENGTH_LONG).show();
          break;
      }
    });
  }

  private void setupListeners() {
    btnStart.setOnClickListener(v -> {
      if (currentSession != null) {
        viewModel.startSession(currentSession.getId(), userId);
      }
    });

    btnComplete.setOnClickListener(v -> {
      if (currentSession != null) {
        viewModel.completeSession(currentSession.getId(), userId);
      }
    });

    btnRefresh.setOnClickListener(v -> {
      loadSession();
    });
  }

  private void loadSession() {
    viewModel.loadUserSession(userId);
  }

  private void displaySession(Session session) {
    tvSessionStatus.setText("Status: " + session.getStatus());

    StringBuilder details = new StringBuilder();
    details.append("Session ID: ").append(session.getId()).append("\n");
    details.append("User A: ").append(session.getUserAId()).append("\n");
    details.append("User B: ").append(session.getUserBId()).append("\n");
    details.append("Scheduled Start: ").append(session.getScheduledStartAt()).append("\n");

    if (session.getStartedAt() != null) {
      details.append("Started At: ").append(session.getStartedAt()).append("\n");
    }

    if (session.getEndedAt() != null) {
      details.append("Ended At: ").append(session.getEndedAt()).append("\n");
    }

    tvSessionDetails.setText(details.toString());
  }

  private void updateButtonStates(String status) {
    btnStart.setEnabled(false);
    btnComplete.setEnabled(false);

    if ("CONFIRMED".equals(status)) {
      btnStart.setEnabled(true);
    } else if ("IN_PROGRESS".equals(status)) {
      btnComplete.setEnabled(true);
    }
  }

  private void showLoading(boolean show) {
    progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    btnStart.setEnabled(!show);
    btnComplete.setEnabled(!show);
    btnRefresh.setEnabled(!show);
  }
}
