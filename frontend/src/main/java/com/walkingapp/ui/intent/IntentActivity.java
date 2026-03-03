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
  private static final int USER_ID = 1;

  private IntentViewModel viewModel;

  private EditText etWalkType;
  private TextView tvStartAt;
  private Spinner spinnerFlexibility;
  private EditText etLat;
  private EditText etLng;
  private EditText etRadius;
  private Button btnCreateIntent;
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
    dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    initViews();
    setupFlexibilitySpinner();
    setupObservers();
    setupListeners();
  }

  private void initViews() {
    etWalkType = findViewById(R.id.etWalkType);
    tvStartAt = findViewById(R.id.tvStartAt);
    spinnerFlexibility = findViewById(R.id.spinnerFlexibility);
    etLat = findViewById(R.id.etLat);
    etLng = findViewById(R.id.etLng);
    etRadius = findViewById(R.id.etRadius);
    btnCreateIntent = findViewById(R.id.btnCreateIntent);
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
          showLoading(false);
          tvStatus.setText("Intent created successfully. Status: " + result.getData().getStatus());
          btnFindMatch.setEnabled(true);
          Toast.makeText(this, "Intent created", Toast.LENGTH_SHORT).show();
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
          tvStatus.setText("Finding match...");
          break;

        case SUCCESS:
          showLoading(false);
          tvStatus.setText("Match found! Proposal ID: " + result.getData().getId());
          Toast.makeText(this, "Match found!", Toast.LENGTH_SHORT).show();

          Intent intent = new Intent(this, ProposalActivity.class);
          intent.putExtra("proposal_id", result.getData().getId());
          intent.putExtra("user_id", USER_ID);
          startActivity(intent);
          break;

        case ERROR:
          showLoading(false);
          tvStatus.setText("Error: " + result.getError());
          Toast.makeText(this, result.getError(), Toast.LENGTH_LONG).show();
          break;
      }
    });
  }

  private void setupListeners() {
    tvStartAt.setOnClickListener(v -> showDateTimePicker());

    btnCreateIntent.setOnClickListener(v -> {
      if (validateInputs()) {
        createIntent();
      }
    });

    btnFindMatch.setOnClickListener(v -> {
      viewModel.findMatch(USER_ID);
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

  private boolean validateInputs() {
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
    String walkType = etWalkType.getText().toString().trim();
    String startAt = dateTimeFormat.format(selectedDateTime.getTime());
    int flexMinutes = spinnerFlexibility.getSelectedItemPosition() == 0 ? 30 : 60;
    double lat = Double.parseDouble(etLat.getText().toString().trim());
    double lng = Double.parseDouble(etLng.getText().toString().trim());
    int radius = Integer.parseInt(etRadius.getText().toString().trim());

    viewModel.createIntent(walkType, startAt, flexMinutes, lat, lng, radius);
  }

  private void showLoading(boolean show) {
    progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    btnCreateIntent.setEnabled(!show);
    btnFindMatch.setEnabled(!show);
  }
}
