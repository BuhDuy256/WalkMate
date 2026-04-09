package com.walkmate.ui.profile.edit;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.util.GlideHelper;

import java.util.Arrays;
import java.util.List;

/**
 * Edit Profile screen.
 *
 * Responsibilities:
 *   1. Pre-fills all form fields from the current profile.
 *   2. Validates and saves changes via EditProfileViewModel.save().
 *   3. Launches the system image picker on avatar tap; delegates byte reading
 *      and upload to EditProfileViewModel.uploadAvatar().
 *   4. Observes saveSuccess → pops back to ProfileFragment.
 */
public class EditProfileFragment extends Fragment {

    public static final String TAG = "EditProfileFragment";

    private static final String[] GENDER_OPTIONS = {"Male", "Female", "Non-binary", "Prefer not to say"};

    // ── Views ─────────────────────────────────────────────────────────────────

    private ProgressBar             progressBar;
    private ImageView               imgAvatar;
    private EditText                etFullName;
    private AutoCompleteTextView    spinnerGender;
    private EditText                etDateOfBirth;
    private EditText                etBio;
    private TextView                txtBioCount;
    private EditText                etSearchRadius;
    private EditText                etTags;
    private Button                  btnSave;
    private View                    btnBack;
    private TextView                txtFieldError;

    // ── MVVM ──────────────────────────────────────────────────────────────────

    private EditProfileViewModel viewModel;

    // ── Image picker ──────────────────────────────────────────────────────────

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK
                                && result.getData() != null) {
                            Uri imageUri = result.getData().getData();
                            if (imageUri != null) {
                                viewModel.uploadAvatar(
                                        imageUri,
                                        requireContext().getContentResolver());
                            }
                        }
                    });

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupViewModel();
        setupClickListeners();
        setupBioCounter();

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
        viewModel.loadCurrentProfile();
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void bindViews(View root) {
        progressBar    = root.findViewById(R.id.progressEditProfile);
        imgAvatar      = root.findViewById(R.id.imgEditAvatar);
        etFullName     = root.findViewById(R.id.etFullName);
        spinnerGender  = root.findViewById(R.id.spinnerGender);
        etDateOfBirth  = root.findViewById(R.id.etDateOfBirth);
        etBio          = root.findViewById(R.id.etBio);
        txtBioCount    = root.findViewById(R.id.txtBioCount);
        etSearchRadius = root.findViewById(R.id.etSearchRadius);
        etTags         = root.findViewById(R.id.etTags);
        btnSave        = root.findViewById(R.id.btnSaveProfile);
        btnBack        = root.findViewById(R.id.btnBackEditProfile);
        txtFieldError  = root.findViewById(R.id.txtEditProfileError);

        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                GENDER_OPTIONS);
        spinnerGender.setAdapter(genderAdapter);
    }

    private void setupViewModel() {
        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        EditProfileViewModelFactory factory =
                new EditProfileViewModelFactory(app.getUserProfileRepository());
        viewModel = new ViewModelProvider(this, factory).get(EditProfileViewModel.class);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        imgAvatar.setOnClickListener(v -> launchImagePicker());

        btnSave.setOnClickListener(v -> {
            String fullName     = etFullName.getText().toString().trim();
            String gender       = spinnerGender.getText().toString().trim();
            String dob          = etDateOfBirth.getText().toString().trim();
            String bio          = etBio.getText().toString().trim();
            int    radius       = parseRadius(etSearchRadius.getText().toString().trim());
            List<String> tags   = parseTags(etTags.getText().toString().trim());

            viewModel.save(fullName, gender, dob, bio, radius, tags);
        });
    }

    private void setupBioCounter() {
        etBio.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                txtBioCount.setText(s.length() + "/500");
            }
        });
    }

    // ── State rendering ───────────────────────────────────────────────────────

    private void renderState(EditProfileUiState state) {
        progressBar.setVisibility(state.isLoading ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!state.isLoading);

        // Error banner
        if (state.fieldError != null) {
            txtFieldError.setText(state.fieldError);
            txtFieldError.setVisibility(View.VISIBLE);
        } else {
            txtFieldError.setVisibility(View.GONE);
        }

        // Pop back on successful save
        if (state.saveSuccess) {
            Toast.makeText(requireContext(), "Profile saved", Toast.LENGTH_SHORT).show();
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
            return;
        }

        // Pre-fill fields (only once when form fields are empty — avoids overwriting typing)
        if (state.fullName != null && etFullName.getText().toString().isEmpty()) {
            etFullName.setText(state.fullName);
        }
        if (state.gender != null && spinnerGender.getText().toString().isEmpty()) {
            spinnerGender.setText(state.gender, false);
        }
        if (state.dateOfBirth != null && etDateOfBirth.getText().toString().isEmpty()) {
            etDateOfBirth.setText(state.dateOfBirth);
        }
        if (state.bio != null && etBio.getText().toString().isEmpty()) {
            etBio.setText(state.bio);
        }
        if (state.searchRadius > 0 && etSearchRadius.getText().toString().isEmpty()) {
            etSearchRadius.setText(String.valueOf(state.searchRadius));
        }
        if (state.tags != null && !state.tags.isEmpty()
                && etTags.getText().toString().isEmpty()) {
            etTags.setText(joinTags(state.tags));
        }

        // Avatar
        GlideHelper.loadCircle(imgAvatar, state.avatarUrl);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void launchImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private static int parseRadius(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Parses a comma-separated tag string into a list.
     * e.g. "Dog Friendly, Chatty" → ["Dog Friendly", "Chatty"]
     */
    private static List<String> parseTags(String raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        String[] parts = raw.split(",");
        java.util.List<String> tags = new java.util.ArrayList<>(parts.length);
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) tags.add(trimmed);
        }
        return tags;
    }

    private static String joinTags(List<String> tags) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(tags.get(i));
        }
        return sb.toString();
    }
}
