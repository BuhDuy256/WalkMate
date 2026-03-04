package com.walkingapp.ui.intent;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.walkingapp.R;
import com.walkingapp.data.repository.WalkingRepository;
import com.walkingapp.ui.session.ProposalActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class IntentActivity extends AppCompatActivity {
  private IntentViewModel viewModel;

  private EditText etUserId;
  private EditText etWalkType;
  private TextView tvStartAt;
  private Spinner spinnerFlexibility;
  private EditText etLat;
  private EditText etLng;
  private EditText etRadius;
  private Button btnFindMatch;
  private ProgressBar progressBar;
  private TextView tvStatus;

  private Calendar selectedDateTime;
  private SimpleDateFormat dateTimeFormat;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_intent);

    viewModel = new ViewModelProvider(this).get(IntentViewModel.class);

    selectedDateTime = Calendar.getInstance();
    dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());

    initViews();
    setupFlexibilitySpinner();
    setupObservers();
    setupListeners();
  }

  private void initViews() {
    etUserId = findViewById(R.id.etUserId);
    etWalkType = findViewById(R.id.etWalkType);
    tvStartAt = findViewById(R.id.tvStartAt);
    spinnerFlexibility = findViewById(R.id.spinnerFlexibility);
    etLat = findViewById(R.id.etLat);
    etLng = findViewById(R.id.etLng);
    etRadius = findViewById(R.id.etRadius);
    btnFindMatch = findViewById(R.id.btnFindMatch);
    progressBar = findViewById(R.id.progressBar);
    tvStatus = findViewById(R.id.tvStatus);
  }

  private void setupFlexibilitySpinner() {
    ArrayAdapter<String> adapter = new ArrayAdapter<>(
        this,
        android.R.layout.simple_spinner_item,
        new String[] { "±30 minutes", "±1 hour" });
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spinnerFlexibility.setAdapter(adapter);
  }

  private void setupObservers() {
    viewModel.getIntentResult().observe(this, result -> {
      if (result == null)
        return;

      switch (result.getStatus()) {
        case LOADING:
          showLoading(true);
          tvStatus.setText("Creating intent...");
          break;

        case SUCCESS:
          android.util.Log.d("IntentActivity", "Intent creation SUCCESS callback triggered");
          if (result.getData() != null) {
            android.util.Log.d("IntentActivity", "Intent data is not null, preparing to find match");
            tvStatus.setText("Intent created. Finding match...");
            // Automatically find match after intent created
            int userId = getUserId();
            android.util.Log.d("IntentActivity", "Got userId: " + userId);
            if (userId > 0) {
              android.util.Log.d("IntentActivity", "Calling viewModel.findMatch with userId: " + userId);
              viewModel.findMatch(userId);
            } else {
              android.util.Log.e("IntentActivity", "userId is <= 0, not calling findMatch");
              showLoading(false);
              Toast.makeText(this, "Invalid user ID", Toast.LENGTH_SHORT).show();
            }
          } else {
            android.util.Log.e("IntentActivity", "Intent data is NULL");
            showLoading(false);
            tvStatus.setText("Intent created but data is null");
            Toast.makeText(this, "Warning: Response data is null", Toast.LENGTH_LONG).show();
          }
          break;

        case ERROR:
          showLoading(false);
          tvStatus.setText("Error: " + result.getError());
          Toast.makeText(this, result.getError(), Toast.LENGTH_LONG).show();
          break;
      }
    });

    viewModel.getMatchResult().observe(this, result -> {
      if (result == null)
        return;

      switch (result.getStatus()) {
        case LOADING:
          showLoading(true);
          btnFindMatch.setEnabled(false);
          tvStatus.setText("Finding match...");
          break;

        case SUCCESS:
          showLoading(false);
          btnFindMatch.setEnabled(true);
          tvStatus.setText("GHÉP THÀNH CÔNG! Proposal ID: " + result.getData().getId());

          // Show success dialog
          new android.app.AlertDialog.Builder(this)
              .setTitle("🎉 GHÉP THÀNH CÔNG!")
              .setMessage("Đã tìm thấy partner phù hợp!\nProposal ID: " + result.getData().getId())
              .setPositiveButton("Xem Chi Tiết", (dialog, which) -> {
                Intent intent = new Intent(this, ProposalActivity.class);
                intent.putExtra("proposal_id", result.getData().getId());
                intent.putExtra("user_id", getUserId());
                startActivity(intent);
              })
              .setNegativeButton("OK", null)
              .show();
          break;

        case ERROR:
          showLoading(false);
          btnFindMatch.setEnabled(true);
          String errorMsg = result.getError();
          if (errorMsg != null && errorMsg.contains("No match found")) {
            tvStatus.setText("Chưa tìm thấy partner phù hợp");
            Toast.makeText(this, "Chưa có ai phù hợp. Thử lại sau!", Toast.LENGTH_LONG).show();
          } else {
            tvStatus.setText("Lỗi: " + errorMsg);
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
          }
          break;
      }
    });
  }

  private void setupListeners() {
    tvStartAt.setOnClickListener(v -> showDateTimePicker());

    btnFindMatch.setOnClickListener(v -> {
      if (validateInputs()) {
        createIntent();
      }
    });
  }

  private void showDateTimePicker() {
    DatePickerDialog datePickerDialog = new DatePickerDialog(
        this,
        (view, year, month, dayOfMonth) -> {
          selectedDateTime.set(Calendar.YEAR, year);
          selectedDateTime.set(Calendar.MONTH, month);
          selectedDateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);

          TimePickerDialog timePickerDialog = new TimePickerDialog(
              this,
              (timeView, hourOfDay, minute) -> {
                selectedDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                selectedDateTime.set(Calendar.MINUTE, minute);
                selectedDateTime.set(Calendar.SECOND, 0);
                tvStartAt.setText(dateTimeFormat.format(selectedDateTime.getTime()));
              },
              selectedDateTime.get(Calendar.HOUR_OF_DAY),
              selectedDateTime.get(Calendar.MINUTE),
              true);
          timePickerDialog.show();
        },
        selectedDateTime.get(Calendar.YEAR),
        selectedDateTime.get(Calendar.MONTH),
        selectedDateTime.get(Calendar.DAY_OF_MONTH));
    datePickerDialog.show();
  }

  private int getUserId() {
    try {
      String userIdText = etUserId.getText().toString().trim();
      if (userIdText.isEmpty()) {
        Toast.makeText(this, "Please enter user ID", Toast.LENGTH_SHORT).show();
        return -1;
      }
      return Integer.parseInt(userIdText);
    } catch (NumberFormatException e) {
      Toast.makeText(this, "Invalid user ID", Toast.LENGTH_SHORT).show();
      return -1;
    }
  }

  private boolean validateInputs() {
    if (getUserId() <= 0) {
      return false;
    }

    if (etWalkType.getText().toString().trim().isEmpty()) {
      Toast.makeText(this, "Please enter walk type", Toast.LENGTH_SHORT).show();
      return false;
    }

    if (tvStartAt.getText().toString().equals("Select Date & Time")) {
      Toast.makeText(this, "Please select start time", Toast.LENGTH_SHORT).show();
      return false;
    }

    if (etLat.getText().toString().trim().isEmpty() || etLng.getText().toString().trim().isEmpty()) {
      Toast.makeText(this, "Please enter location", Toast.LENGTH_SHORT).show();
      return false;
    }

    if (etRadius.getText().toString().trim().isEmpty()) {
      Toast.makeText(this, "Please enter radius", Toast.LENGTH_SHORT).show();
      return false;
    }

    return true;
  }

  private void createIntent() {
    int userId = getUserId();
    String walkType = etWalkType.getText().toString().trim();
    String startAt = dateTimeFormat.format(selectedDateTime.getTime());
    int flexMinutes = spinnerFlexibility.getSelectedItemPosition() == 0 ? 30 : 60;
    double lat = Double.parseDouble(etLat.getText().toString().trim());
    double lng = Double.parseDouble(etLng.getText().toString().trim());
    int radius = Integer.parseInt(etRadius.getText().toString().trim());

    viewModel.createIntent(userId, walkType, startAt, flexMinutes, lat, lng, radius);
  }

  private void showLoading(boolean show) {
    progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    btnFindMatch.setEnabled(!show);
  }
}
