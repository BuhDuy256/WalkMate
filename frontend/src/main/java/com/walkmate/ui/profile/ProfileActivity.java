package com.walkmate.ui.profile;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.app.DatePickerDialog;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
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
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ProfileActivity extends AppCompatActivity {
    private static final int MAX_BIO_LENGTH = 120;

    private ProfileViewModel viewModel;

    private ImageView ivAvatar;
    private ImageView ivAvatarCamera;
    private TextInputEditText etDisplayName;
    private TextInputEditText etCity;
    private TextInputEditText etDateOfBirth;
    private AutoCompleteTextView acGender;
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

    private ActivityResultLauncher<PickVisualMediaRequest> avatarPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        initViews();
        setupAvatarPicker();
        initViewModel();
        setupListeners();
        observeState();
        observeEffect();
    }

    private void initViews() {
        ivAvatar = findViewById(R.id.iv_avatar);
        ivAvatarCamera = findViewById(R.id.iv_avatar_camera);
        etDisplayName = findViewById(R.id.et_display_name);
        etCity = findViewById(R.id.et_city);
        etDateOfBirth = findViewById(R.id.et_date_of_birth);
        acGender = findViewById(R.id.ac_gender);
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

    private void setupAvatarPicker() {
        avatarPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        ivAvatar.setImageURI(uri);
                    }
                }
        );

        View.OnClickListener avatarClickListener = v -> avatarPickerLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()
        );

        if (ivAvatar != null) {
            ivAvatar.setOnClickListener(avatarClickListener);
        }
        if (ivAvatarCamera != null) {
            ivAvatarCamera.setOnClickListener(avatarClickListener);
        }
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
        setupDateOfBirthPicker();
        setupGenderDropdown();

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

        if (swProfileMode != null) {
            swProfileMode.setOnCheckedChangeListener((buttonView, isChecked) ->
                    viewModel.onEvent(new ProfileUiEvent.ProfileModeChanged(isChecked ? ProfileMode.PUBLIC : ProfileMode.PRIVATE))
            );
        }

        if (swInfoVisibility != null) {
            swInfoVisibility.setOnCheckedChangeListener((buttonView, isChecked) ->
                    viewModel.onEvent(new ProfileUiEvent.InfoVisibilityChanged(isChecked ? InfoVisibilityMode.PUBLIC : InfoVisibilityMode.PRIVATE))
            );
        }

        btnSave.setOnClickListener(v -> viewModel.onEvent(new ProfileUiEvent.SaveClicked()));
    }

    private void setupDateOfBirthPicker() {
        if (etDateOfBirth == null) {
            return;
        }

        View.OnClickListener openPicker = v -> {
            Calendar calendar = Calendar.getInstance();
            DatePickerDialog dialog = new DatePickerDialog(
                    ProfileActivity.this,
                    (view, year, month, dayOfMonth) -> etDateOfBirth.setText(String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year)),
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            dialog.show();
        };

        etDateOfBirth.setOnClickListener(openPicker);
        etDateOfBirth.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                openPicker.onClick(v);
            }
        });
    }

    private void setupGenderDropdown() {
        if (acGender == null) {
            return;
        }

        String[] genderOptions = new String[]{"Female", "Male"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, genderOptions);
        acGender.setAdapter(adapter);
        acGender.setThreshold(0);
        acGender.setOnClickListener(v -> acGender.showDropDown());
        acGender.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                acGender.showDropDown();
            }
        });
    }

    private void observeState() {
        viewModel.uiState.observe(this, this::renderState);
    }

    private void observeEffect() {
        viewModel.uiEffect.observe(this, this::handleEffect);
    }

    private void renderState(ProfileUiState state) {
        ProfileViewData data = state.getData();

        progressLoading.setVisibility(View.GONE);
        btnSave.setEnabled(state.isSaveEnabled() && !state.isLoading() && !state.isSaving());

        setTextIfDifferent(etDisplayName, data.getFullName());
        setTextIfDifferent(etCity, data.getCity());
        setTextIfDifferent(etBio, data.getBio());
        tvBioCount.setText(data.getBio().length() + "/" + MAX_BIO_LENGTH);

        if (swProfileMode != null) {
            swProfileMode.setChecked(data.getProfileMode() == ProfileMode.PUBLIC);
        }
        if (swInfoVisibility != null) {
            swInfoVisibility.setChecked(data.getInfoVisibilityMode() == InfoVisibilityMode.PUBLIC);
        }
        if (tvInfoModeDescription != null) {
            tvInfoModeDescription.setText(data.getInfoVisibilityMode() == InfoVisibilityMode.PUBLIC
                    ? "Your contact info can be seen in public profile."
                    : "Your contact info is private (only you can see it).");
        }

        List<ProfileViewData.TagViewData> interests = data.getTagsByCategory("INTERESTS");
        List<ProfileViewData.TagViewData> walkVibes = data.getTagsByCategory("WALK_VIBES");
        List<ProfileViewData.TagViewData> bestTime = data.getTagsByCategory("BEST_TIME");
        tvInterestsCount.setText(getSelectedInCategory(interests) + " \u2713");
        tvWalkVibesCount.setText(getSelectedInCategory(walkVibes) + " \u2713");
        tvBestTimeCount.setText(getSelectedInCategory(bestTime) + " \u2713");

        if (cgInterests != null) {
            renderChipGroup(cgInterests, interests);
        }
        if (cgWalkVibes != null) {
            renderChipGroup(cgWalkVibes, walkVibes);
        }
        if (cgBestTime != null) {
            renderChipGroup(cgBestTime, bestTime);
        }

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

    private void renderChipGroup(ChipGroup chipGroup, List<ProfileViewData.TagViewData> tags) {
        chipGroup.removeAllViews();
        for (ProfileViewData.TagViewData tag : tags) {
            ChipVisualStyle style = getChipStyle(tag.getCode());

            Chip chip = new Chip(this);
            chip.setText(style.emoji + "  " + tag.getLabel());
            chip.setTextAppearance(R.style.TextAppearance_WalkMate_ProfileChip);
            chip.setTextColor(ContextCompat.getColor(this, style.textColorRes));
            chip.setCheckable(true);
            chip.setChecked(tag.isSelected());
            chip.setEnsureMinTouchTargetSize(false);
            chip.setChipMinHeight(dpToPx(44));
            chip.setChipCornerRadius(dpToPx(22));
            chip.setChipBackgroundColorResource(android.R.color.transparent);
            chip.setChipStrokeWidth(0f);
            chip.setRippleColorResource(R.color.profile_card);
            chip.setChipStartPadding(dpToPx(14));
            chip.setChipEndPadding(dpToPx(12));
            chip.setTextStartPadding(dpToPx(4));
            chip.setTextEndPadding(dpToPx(6));
            chip.setBackgroundResource(style.backgroundDrawableRes);

            chip.setCloseIconVisible(tag.isSelected());
            chip.setCloseIconResource(R.drawable.ic_chip_check_bold_16);
            chip.setCloseIconTintResource(style.textColorRes);
            chip.setCloseIconSize(dpToPx(16));
            chip.setCloseIconStartPadding(dpToPx(8));
            chip.setCloseIconEndPadding(dpToPx(2));
            chip.setOnCloseIconClickListener(v -> chip.toggle());

            chip.setOnCheckedChangeListener((buttonView, isChecked) ->
                    viewModel.onEvent(new ProfileUiEvent.TagToggled(tag.getCode()))
            );

            chipGroup.addView(chip);
        }
    }

    private ChipVisualStyle getChipStyle(String tagCode) {
        switch (tagCode) {
            case "PET_WALKING":
                return new ChipVisualStyle("\uD83D\uDC3E", R.drawable.profile_chip_orange, R.color.profile_chip_text_brown);
            case "INDIE_MUSIC":
                return new ChipVisualStyle("\uD83C\uDFB5", R.drawable.profile_chip_purple, R.color.profile_chip_text_purple);
            case "PHOTOGRAPHY":
                return new ChipVisualStyle("\uD83D\uDCF8", R.drawable.profile_chip_pink, R.color.profile_chip_text_red);
            case "NATURE_LOVER":
                return new ChipVisualStyle("\uD83C\uDF43", R.drawable.profile_chip_mint, R.color.profile_chip_text_green);
            case "COFFEE_WALKS":
                return new ChipVisualStyle("\u2615", R.drawable.profile_chip_cream, R.color.profile_chip_text_brown);
            case "BOOK_CLUB":
                return new ChipVisualStyle("\uD83D\uDCDA", R.drawable.profile_chip_blue, R.color.profile_chip_text_blue);
            case "PODCAST_LISTENER":
                return new ChipVisualStyle("\uD83C\uDFA4", R.drawable.profile_chip_lavender, R.color.profile_chip_text_purple);
            case "STREET_ART":
                return new ChipVisualStyle("\uD83C\uDFA8", R.drawable.profile_chip_yellow, R.color.profile_chip_text_brown);
            case "FOODIE":
                return new ChipVisualStyle("\uD83C\uDF5C", R.drawable.profile_chip_orange, R.color.profile_chip_text_brown);
            case "YOGA_WELLNESS":
                return new ChipVisualStyle("\uD83E\uDDD8", R.drawable.profile_chip_mint, R.color.profile_chip_text_green);
            case "QUIET_WALK":
                return new ChipVisualStyle("\uD83E\uDD2B", R.drawable.profile_chip_blue, R.color.profile_chip_text_blue);
            case "CHATTY_SOCIAL":
                return new ChipVisualStyle("\uD83D\uDCAC", R.drawable.profile_chip_cream, R.color.profile_chip_text_brown);
            case "CHALLENGE_PACE":
                return new ChipVisualStyle("\u26A1", R.drawable.profile_chip_yellow, R.color.profile_chip_text_brown);
            case "SLOW_SCENIC":
                return new ChipVisualStyle("\uD83C\uDF07", R.drawable.profile_chip_red, R.color.profile_chip_text_red);
            case "CITY_EXPLORER":
                return new ChipVisualStyle("\uD83C\uDFD9", R.drawable.profile_chip_purple, R.color.profile_chip_text_purple);
            case "FOREST_TRAILS":
                return new ChipVisualStyle("\uD83C\uDF32", R.drawable.profile_chip_mint, R.color.profile_chip_text_green);
            case "MORNING_BIRD":
                return new ChipVisualStyle("\uD83C\uDF05", R.drawable.profile_chip_yellow, R.color.profile_chip_text_brown);
            case "MIDDAY_BREAK":
                return new ChipVisualStyle("\u2600", R.drawable.profile_chip_cream, R.color.profile_chip_text_brown);
            case "GOLDEN_HOUR":
                return new ChipVisualStyle("\uD83C\uDF07", R.drawable.profile_chip_orange, R.color.profile_chip_text_red);
            case "NIGHT_OWL":
                return new ChipVisualStyle("\uD83C\uDF19", R.drawable.profile_chip_night, R.color.profile_chip_text_purple);
            case "WEEKENDS_ONLY":
                return new ChipVisualStyle("\uD83D\uDCC5", R.drawable.profile_chip_lavender, R.color.profile_chip_text_purple);
            case "FLEXIBLE":
                return new ChipVisualStyle("\uD83D\uDD04", R.drawable.profile_chip_mint, R.color.profile_chip_text_green);
            default:
                return new ChipVisualStyle("\u2728", R.drawable.profile_chip_cream, R.color.profile_chip_text_brown);
        }
    }

    private float dpToPx(int dp) {
        return dp * getResources().getDisplayMetrics().density;
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

    private static class ChipVisualStyle {
        final String emoji;
        final int backgroundDrawableRes;
        final int textColorRes;

        ChipVisualStyle(String emoji, int backgroundDrawableRes, int textColorRes) {
            this.emoji = emoji;
            this.backgroundDrawableRes = backgroundDrawableRes;
            this.textColorRes = textColorRes;
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
