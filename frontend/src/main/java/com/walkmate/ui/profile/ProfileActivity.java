package com.walkmate.ui.profile;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.walkmate.R;
import com.walkmate.data.datasource.remote.api.ProfileApiService;
import com.walkmate.data.mapper.ProfileDomainToDtoMapper;
import com.walkmate.data.mapper.ProfileDtoToDomainMapper;
import com.walkmate.data.network.ApiClient;
import com.walkmate.data.repository.ProfileRepositoryImpl;
import com.walkmate.domain.profile.InfoVisibilityMode;
import com.walkmate.domain.profile.ProfileMode;
import com.walkmate.domain.profile.ProfileRepository;
import com.walkmate.domain.profile.ProfileService;

import java.util.List;
import java.util.UUID;

public class ProfileActivity extends AppCompatActivity {
    private static final int MAX_BIO_LENGTH = 120;

    private ProfileViewModel viewModel;

    private ImageButton btnBack;
    private TextView tvSelectedCount;
    private TextView tvReadyStatus;
    private ProgressBar progressSelection;
    private TextInputEditText etDisplayName;
    private TextInputEditText etCity;
    private TextInputEditText etBio;
    private TextView tvBioCount;
    private MaterialSwitch swProfileMode;
    private MaterialSwitch swInfoVisibility;
    private TextView tvInfoModeDescription;
    private ChipGroup cgInterests;
    private ChipGroup cgWalkVibes;
    private ChipGroup cgBestTime;
    private TextView tvInterestsCount;
    private TextView tvWalkVibesCount;
    private TextView tvBestTimeCount;
    private TextView tvError;
    private ProgressBar progressLoading;
    private MaterialButton btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initViews();
        initViewModel();
        setupListeners();
        observeState();
        observeEffect();
    }

    private void initViews() {
        tvSelectedCount = findViewById(R.id.tv_selected_count);
        tvReadyStatus = findViewById(R.id.tv_ready_status);
        progressSelection = findViewById(R.id.progress_selection);
        etDisplayName = findViewById(R.id.et_display_name);
        etCity = findViewById(R.id.et_city);
        etBio = findViewById(R.id.et_bio);
        tvBioCount = findViewById(R.id.tv_bio_count);
        swProfileMode = findViewById(R.id.sw_profile_mode);
        swInfoVisibility = findViewById(R.id.sw_info_visibility);
        tvInfoModeDescription = findViewById(R.id.tv_info_mode_description);
        cgInterests = findViewById(R.id.cg_interests);
        cgWalkVibes = findViewById(R.id.cg_walk_vibes);
        cgBestTime = findViewById(R.id.cg_best_time);
        tvInterestsCount = findViewById(R.id.tv_interests_count);
        tvWalkVibesCount = findViewById(R.id.tv_walk_vibes_count);
        tvBestTimeCount = findViewById(R.id.tv_best_time_count);
        tvError = findViewById(R.id.tv_error);
        progressLoading = findViewById(R.id.progress_loading);
        btnSave = findViewById(R.id.btn_save);
    }

    private void initViewModel() {
        UUID ownerId = resolveUuid("PROFILE_OWNER_ID", resolveUuid("USER_ID", UUID.randomUUID()));
        UUID viewerId = resolveUuid("VIEWER_ID", ownerId);

        ProfileApiService apiService = ApiClient.getProfileApi();
        ProfileRepository repository = new ProfileRepositoryImpl(
                apiService,
                new ProfileDomainToDtoMapper(),
                new ProfileDtoToDomainMapper()
        );
        ProfileService service = new ProfileService(repository);

        ProfileViewModelFactory factory = new ProfileViewModelFactory(service, ownerId, viewerId);
        viewModel = new ViewModelProvider(this, factory).get(ProfileViewModel.class);
    }

    private UUID resolveUuid(String key, UUID defaultValue) {
        if (getIntent().hasExtra(key)) {
            String value = getIntent().getStringExtra(key);
            if (value != null && !value.trim().isEmpty()) {
                return UUID.fromString(value);
            }
        }
        return defaultValue;
    }

    private void setupListeners() {
       

        etDisplayName.addTextChangedListener(new SimpleTextWatcher(s ->
                viewModel.onEvent(new ProfileUiEvent.NameChanged(s))
        ));

        etCity.addTextChangedListener(new SimpleTextWatcher(s ->
                viewModel.onEvent(new ProfileUiEvent.CityChanged(s))
        ));

        etBio.addTextChangedListener(new SimpleTextWatcher(s -> {
            viewModel.onEvent(new ProfileUiEvent.BioChanged(s));
            tvBioCount.setText(s.length() + "/" + MAX_BIO_LENGTH);
        }));

        swProfileMode.setOnCheckedChangeListener((buttonView, isChecked) ->
                viewModel.onEvent(new ProfileUiEvent.ProfileModeChanged(isChecked ? ProfileMode.PUBLIC : ProfileMode.PRIVATE))
        );

        swInfoVisibility.setOnCheckedChangeListener((buttonView, isChecked) ->
                viewModel.onEvent(new ProfileUiEvent.InfoVisibilityChanged(isChecked ? InfoVisibilityMode.PUBLIC : InfoVisibilityMode.PRIVATE))
        );

        btnSave.setOnClickListener(v -> viewModel.onEvent(new ProfileUiEvent.SaveClicked()));
    }

    private void observeState() {
        viewModel.uiState.observe(this, this::renderState);
    }

    private void observeEffect() {
        viewModel.uiEffect.observe(this, this::handleEffect);
    }

    private void renderState(ProfileUiState state) {
        ProfileViewData data = state.getData();

        progressLoading.setVisibility((state.isLoading() || state.isSaving()) ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(state.isSaveEnabled() && !state.isLoading() && !state.isSaving());

        setTextIfDifferent(etDisplayName, data.getFullName());
        setTextIfDifferent(etCity, data.getCity());
        setTextIfDifferent(etBio, data.getBio());
        tvBioCount.setText(data.getBio().length() + "/" + MAX_BIO_LENGTH);

        swProfileMode.setChecked(data.getProfileMode() == ProfileMode.PUBLIC);
        swInfoVisibility.setChecked(data.getInfoVisibilityMode() == InfoVisibilityMode.PUBLIC);
        tvInfoModeDescription.setText(data.getInfoVisibilityMode() == InfoVisibilityMode.PUBLIC
                ? "Your contact info can be seen in public profile."
                : "Your contact info is private (only you can see it).");

        int selectedCount = data.getSelectedCount();
        tvSelectedCount.setText(selectedCount + " tags selected - 3 min required");
        tvReadyStatus.setText(data.canSave() ? "Ready to save" : "Add more info");
        progressSelection.setProgress(Math.min(100, selectedCount * 100 / 6));

        List<ProfileViewData.TagViewData> interests = data.getTagsByCategory("INTERESTS");
        List<ProfileViewData.TagViewData> walkVibes = data.getTagsByCategory("WALK_VIBES");
        List<ProfileViewData.TagViewData> bestTime = data.getTagsByCategory("BEST_TIME");
        tvInterestsCount.setText(getSelectedInCategory(interests) + " selected");
        tvWalkVibesCount.setText(getSelectedInCategory(walkVibes) + " selected");
        tvBestTimeCount.setText(getSelectedInCategory(bestTime) + " selected");
        renderChipGroup(cgInterests, interests, R.color.profile_chip_interest_checked, R.color.profile_chip_interest_unchecked);
        renderChipGroup(cgWalkVibes, walkVibes, R.color.profile_chip_vibe_checked, R.color.profile_chip_vibe_unchecked);
        renderChipGroup(cgBestTime, bestTime, R.color.profile_chip_time_checked, R.color.profile_chip_time_unchecked);

        if (state.getError() != null && !state.getError().isEmpty()) {
            tvError.setText(state.getError());
            tvError.setVisibility(View.VISIBLE);
        } else {
            tvError.setVisibility(View.GONE);
        }
    }

    private int getSelectedInCategory(List<ProfileViewData.TagViewData> tags) {
        int count = 0;
        for (ProfileViewData.TagViewData tag : tags) {
            if (tag.isSelected()) {
                count++;
            }
        }
        return count;
    }

    private void renderChipGroup(
            ChipGroup chipGroup,
            List<ProfileViewData.TagViewData> tags,
            int checkedColor,
            int uncheckedColor
    ) {
        chipGroup.removeAllViews();
        for (ProfileViewData.TagViewData tag : tags) {
            Chip chip = new Chip(this);
            chip.setText(tag.getLabel());
            chip.setCheckable(true);
            chip.setChecked(tag.isSelected());
            chip.setChipBackgroundColorResource(tag.isSelected() ? checkedColor : uncheckedColor);
            chip.setTextColor(ContextCompat.getColor(this, R.color.profile_text_primary));
            chip.setOnCheckedChangeListener((buttonView, isChecked) ->
                    viewModel.onEvent(new ProfileUiEvent.TagToggled(tag.getCode()))
            );
            chipGroup.addView(chip);
        }
    }

    private void setTextIfDifferent(TextInputEditText editText, String value) {
        String current = editText.getText() == null ? "" : editText.getText().toString();
        String target = value == null ? "" : value;
        if (!current.equals(target)) {
            editText.setText(target);
            editText.setSelection(target.length());
        }
    }

    private void handleEffect(ProfileUiEffect effect) {
        if (effect instanceof ProfileUiEffect.ShowToast) {
            Toast.makeText(this, ((ProfileUiEffect.ShowToast) effect).getMessage(), Toast.LENGTH_SHORT).show();
        } else if (effect instanceof ProfileUiEffect.NavigateBack) {
            finish();
        } else if (effect instanceof ProfileUiEffect.SaveSuccess) {
            // Keep screen visible after saving; success is already shown via toast.
        }
    }

    private static class SimpleTextWatcher implements TextWatcher {
        private final OnTextChanged callback;

        SimpleTextWatcher(OnTextChanged callback) {
            this.callback = callback;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            callback.onChanged(s == null ? "" : s.toString());
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }

    private interface OnTextChanged {
        void onChanged(String value);
    }
}
