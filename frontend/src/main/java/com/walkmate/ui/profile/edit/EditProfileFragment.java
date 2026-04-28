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
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import androidx.navigation.fragment.NavHostFragment;

import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.util.GlideHelper;
import com.walkmate.domain.user.ProfileTagMaster;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Edit Profile screen.
 *
 * Tags are presented as a multi-select chip group populated from the master-tags API,
 * replacing the former free-text input. Selected chips yield UUID tag IDs sent to the backend.
 */
public class EditProfileFragment extends Fragment {

    public static final String TAG = "EditProfileFragment";

    private static final String[] GENDER_OPTIONS = {"Male", "Female"};

    // ── Views ─────────────────────────────────────────────────────────────────

    private ProgressBar          progressBar;
    private ImageView            imgAvatar;
    private EditText             etFullName;
    private AutoCompleteTextView spinnerGender;
    private EditText             etDateOfBirth;
    private EditText             etBio;
    private TextView             txtBioCount;
    private ChipGroup            chipGroupTags;
    private Button               btnSave;
    private View                 btnBack;
    private TextView             txtFieldError;

    // Tracks the DOB selected via DatePickerDialog in yyyy-MM-dd format.
    private String selectedDob = "";
    private boolean tagsPreSelected = false;

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

        applyWindowInsets(view);

        bindViews(view);
        setupViewModel();
        setupClickListeners();
        setupBioCounter();

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
        viewModel.loadCurrentProfile();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    private void applyWindowInsets(View root) {
        View contentRoot = root.findViewById(R.id.editProfileContentRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int topInset    = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            int sidePadding = (int) (20 * root.getResources().getDisplayMetrics().density);
            contentRoot.setPadding(sidePadding, topInset + sidePadding,
                                   sidePadding, bottomInset + sidePadding);
            return insets;
        });
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
        chipGroupTags  = root.findViewById(R.id.chipGroupTags);
        btnSave        = root.findViewById(R.id.btnSaveProfile);
        btnBack        = root.findViewById(R.id.btnBackEditProfile);
        txtFieldError  = root.findViewById(R.id.txtEditProfileError);

        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                GENDER_OPTIONS);
        spinnerGender.setAdapter(genderAdapter);
        // inputType="none" suppresses keyboard but also blocks the default auto-show;
        // force-show the dropdown on every tap.
        spinnerGender.setOnClickListener(v -> spinnerGender.showDropDown());
    }

    private void setupViewModel() {
        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        EditProfileViewModelFactory factory =
                new EditProfileViewModelFactory(app.getUserProfileRepository());
        viewModel = new ViewModelProvider(this, factory).get(EditProfileViewModel.class);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        imgAvatar.setOnClickListener(v -> launchImagePicker());

        etDateOfBirth.setOnClickListener(v -> showDatePicker());

        btnSave.setOnClickListener(v -> {
            String fullName      = etFullName.getText().toString().trim();
            String displayGender = spinnerGender.getText().toString().trim();
            String gender        = toApiGender(displayGender);
            String dob           = selectedDob.isEmpty() ? null : selectedDob;
            String bio           = etBio.getText().toString().trim();
            List<String> tagIds  = collectSelectedTagIds();

            viewModel.save(fullName, gender, dob, bio, tagIds);
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

        if (state.fieldError != null) {
            txtFieldError.setText(state.fieldError);
            txtFieldError.setVisibility(View.VISIBLE);
        } else {
            txtFieldError.setVisibility(View.GONE);
        }

        if (state.saveSuccess) {
            Toast.makeText(requireContext(), "Profile saved", Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }

        // Pre-fill text fields (only once — avoids overwriting in-progress edits).
        if (state.fullName != null && etFullName.getText().toString().isEmpty()) {
            etFullName.setText(state.fullName);
        }
        if (state.gender != null && spinnerGender.getText().toString().isEmpty()) {
            spinnerGender.setText(toDisplayGender(state.gender), false);
        }
        if (state.dateOfBirth != null && selectedDob.isEmpty()) {
            selectedDob = state.dateOfBirth;
            etDateOfBirth.setText(state.dateOfBirth);
        }
        if (state.bio != null && etBio.getText().toString().isEmpty()) {
            etBio.setText(state.bio);
        }

        // Populate master tag chips whenever the list arrives (first time only).
        if (!state.masterTags.isEmpty() && chipGroupTags.getChildCount() == 0) {
            populateTagChips(state.masterTags, state.currentTagNames);
            tagsPreSelected = true;
        }

        // Avatar
        GlideHelper.loadCircle(imgAvatar, state.avatarUrl);
    }

    // ── Tag chip helpers ──────────────────────────────────────────────────────

    private void populateTagChips(List<ProfileTagMaster> masterTags, List<String> selectedNames) {
        chipGroupTags.removeAllViews();
        for (ProfileTagMaster tag : masterTags) {
            Chip chip = new Chip(requireContext());
            chip.setText(tag.getTagName());
            chip.setTag(tag.getTagId());
            chip.setCheckable(true);
            boolean isSelected = selectedNames != null && selectedNames.contains(tag.getTagName());
            chip.setChecked(isSelected);
            chipGroupTags.addView(chip);
        }
    }

    private List<String> collectSelectedTagIds() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < chipGroupTags.getChildCount(); i++) {
            View child = chipGroupTags.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                Object tagId = child.getTag();
                if (tagId instanceof String) result.add((String) tagId);
            }
        }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void launchImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();

        // Initialise picker to the already-selected date if available.
        if (!selectedDob.isEmpty()) {
            try {
                cal.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDob));
            } catch (Exception ignored) {}
        }

        android.app.DatePickerDialog dialog = new android.app.DatePickerDialog(
                requireContext(),
                (view, year, month, day) -> {
                    cal.set(year, month, day);
                    selectedDob = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(cal.getTime());
                    etDateOfBirth.setText(selectedDob);
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH));

        // Birth date cannot be in the future.
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private static String toApiGender(String display) {
        if ("Male".equalsIgnoreCase(display))   return "MALE";
        if ("Female".equalsIgnoreCase(display)) return "FEMALE";
        return null;
    }

    private static String toDisplayGender(String api) {
        if ("MALE".equals(api))   return "Male";
        if ("FEMALE".equals(api)) return "Female";
        return "";
    }
}
