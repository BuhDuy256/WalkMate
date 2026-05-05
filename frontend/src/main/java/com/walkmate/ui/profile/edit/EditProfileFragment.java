package com.walkmate.ui.profile.edit;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.util.GlideHelper;
import com.walkmate.domain.user.ProfileTagMaster;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EditProfileFragment extends Fragment {

    public static final String TAG = "EditProfileFragment";

    // ── DOB data ─────────────────────────────────────────────────────────────

    private static final String[] DAYS   = buildDays();
    private static final String[] MONTHS = {"Jan","Feb","Mar","Apr","May","Jun",
                                             "Jul","Aug","Sep","Oct","Nov","Dec"};
    private static final String[] YEARS  = buildYears();

    // ── Views ─────────────────────────────────────────────────────────────────

    private ProgressBar  progressBar;
    private ImageView    imgAvatar;
    private EditText     etFullName;
    private LinearLayout cardGenderMale, cardGenderFemale;
    private View         dotGenderMale, dotGenderFemale;
    private TextView     txtGenderMale, txtGenderFemale;
    private LinearLayout dobBar, segDay, segMonth, segYear;
    private TextView     txtDobDay, txtDobMonth, txtDobYear;
    private ImageView    icChevronDay, icChevronMonth, icChevronYear;
    private EditText     etBio;
    private TextView     txtBioCount;
    private ChipGroup    chipGroupTags;
    private Button       btnSave;
    private View         btnBack;
    private TextView     txtFieldError;

    // ── State ─────────────────────────────────────────────────────────────────

    private String selectedGender = "";
    private String selectedDobDay   = "";
    private String selectedDobMonth = "";
    private String selectedDobYear  = "";

    // Originals captured after first profile load — used for dirty checking.
    private String  origFullName = null;
    private String  origGender   = null;
    private String  origDobDay   = null;
    private String  origDobMonth = null;
    private String  origDobYear  = null;
    private String  origBio      = null;
    private Set<String> origTagNames = null;

    private ListPopupWindow openPopup = null;

    // ── MVVM ─────────────────────────────────────────────────────────────────

    private EditProfileViewModel viewModel;

    // ── Image picker ─────────────────────────────────────────────────────────

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK
                                && result.getData() != null) {
                            Uri imageUri = result.getData().getData();
                            if (imageUri != null) {
                                viewModel.uploadAvatar(imageUri,
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

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void applyWindowInsets(View root) {
        com.walkmate.core.util.WindowInsetUtils.applyStatusBarPadding(root.findViewById(R.id.subPageHeader));
        com.walkmate.core.util.WindowInsetUtils.applyNavBarPadding(root.findViewById(R.id.editProfileContentRoot));
    }

    private void bindViews(View root) {
        progressBar    = root.findViewById(R.id.progressEditProfile);
        imgAvatar      = root.findViewById(R.id.imgEditAvatar);
        etFullName     = root.findViewById(R.id.etFullName);
        cardGenderMale   = root.findViewById(R.id.cardGenderMale);
        cardGenderFemale = root.findViewById(R.id.cardGenderFemale);
        dotGenderMale    = root.findViewById(R.id.dotGenderMale);
        dotGenderFemale  = root.findViewById(R.id.dotGenderFemale);
        txtGenderMale    = root.findViewById(R.id.txtGenderMale);
        txtGenderFemale  = root.findViewById(R.id.txtGenderFemale);
        dobBar           = root.findViewById(R.id.dobBar);
        segDay           = root.findViewById(R.id.segDay);
        segMonth         = root.findViewById(R.id.segMonth);
        segYear          = root.findViewById(R.id.segYear);
        txtDobDay        = root.findViewById(R.id.txtDobDay);
        txtDobMonth      = root.findViewById(R.id.txtDobMonth);
        txtDobYear       = root.findViewById(R.id.txtDobYear);
        icChevronDay     = root.findViewById(R.id.icChevronDay);
        icChevronMonth   = root.findViewById(R.id.icChevronMonth);
        icChevronYear    = root.findViewById(R.id.icChevronYear);
        etBio          = root.findViewById(R.id.etBio);
        txtBioCount    = root.findViewById(R.id.txtBioCount);
        chipGroupTags  = root.findViewById(R.id.chipGroupTags);
        btnSave        = root.findViewById(R.id.btnSaveProfile);
        btnBack        = root.findViewById(R.id.btnSubPageBack);
        txtFieldError  = root.findViewById(R.id.txtEditProfileError);

        ((TextView) root.findViewById(R.id.txtSubPageTitle)).setText("Edit Profile");

        // Clip avatar ImageView to circle using ViewOutlineProvider.
        imgAvatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        imgAvatar.setClipToOutline(true);
        imgAvatar.setElevation(dpToPx(2));
    }

    private void setupViewModel() {
        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        EditProfileViewModelFactory factory =
                new EditProfileViewModelFactory(app.getUserProfileRepository());
        viewModel = new ViewModelProvider(this, factory).get(EditProfileViewModel.class);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> {
            dismissOpenPopup();
            NavHostFragment.findNavController(this).popBackStack();
        });

        ImageButton btnAvatarBadge = requireView().findViewById(R.id.btnEditAvatarBadge);
        imgAvatar.setOnClickListener(v -> launchImagePicker());
        btnAvatarBadge.setOnClickListener(v -> launchImagePicker());

        cardGenderMale.setOnClickListener(v -> selectGender("MALE"));
        cardGenderFemale.setOnClickListener(v -> selectGender("FEMALE"));

        segDay.setOnClickListener(v -> showDobPopup(segDay, txtDobDay, icChevronDay, DAYS));
        segMonth.setOnClickListener(v -> showDobPopup(segMonth, txtDobMonth, icChevronMonth, MONTHS));
        segYear.setOnClickListener(v -> showDobPopup(segYear, txtDobYear, icChevronYear, YEARS));

        btnSave.setOnClickListener(v -> {
            String fullName = etFullName.getText().toString().trim();
            String gender   = selectedGender.isEmpty() ? null : selectedGender;
            String dob      = buildDobString();
            String bio      = etBio.getText().toString().trim();
            List<String> tagIds = collectSelectedTagIds();
            viewModel.save(fullName, gender, dob, bio, tagIds);
        });
    }

    private void setupBioCounter() {
        etBio.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                txtBioCount.setText(s.length() + "/500");
                checkDirty();
            }
        });
        etFullName.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) { checkDirty(); }
        });
    }

    // ── State rendering ───────────────────────────────────────────────────────

    private void renderState(EditProfileUiState state) {
        progressBar.setVisibility(state.isLoading ? View.VISIBLE : View.GONE);

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

        // Pre-fill only once (avoid overwriting in-progress edits).
        if (state.fullName != null && etFullName.getText().toString().isEmpty()) {
            etFullName.setText(state.fullName);
        }
        if (state.gender != null && selectedGender.isEmpty()) {
            selectGender(state.gender);
        }
        if (state.dateOfBirth != null && selectedDobDay.isEmpty()) {
            parseDobIntoSegments(state.dateOfBirth);
        }
        if (state.bio != null && etBio.getText().toString().isEmpty()) {
            etBio.setText(state.bio);
            txtBioCount.setText(state.bio.length() + "/500");
        }
        if (!state.masterTags.isEmpty() && chipGroupTags.getChildCount() == 0) {
            populateTagChips(state.masterTags, state.currentTagNames);
            checkDirty(); // re-evaluate now that tag chips are rendered
        }

        GlideHelper.loadCircle(imgAvatar, state.avatarUrl);

        // Capture originals once, after the first full load (all fields ready).
        if (origFullName == null && state.fullName != null) {
            origFullName  = state.fullName;
            origGender    = state.gender != null ? state.gender : "";
            String[] dob  = parseDobToArray(state.dateOfBirth);
            origDobDay    = dob[0]; origDobMonth = dob[1]; origDobYear = dob[2];
            origBio       = state.bio != null ? state.bio : "";
            origTagNames  = new HashSet<>(state.currentTagNames != null
                                          ? state.currentTagNames : new ArrayList<>());
            checkDirty();
        }
    }

    // ── Gender toggle ─────────────────────────────────────────────────────────

    private void selectGender(String apiGender) {
        selectedGender = apiGender;
        boolean isMale = "MALE".equals(apiGender);
        applyGenderCard(cardGenderMale, dotGenderMale, txtGenderMale, isMale);
        applyGenderCard(cardGenderFemale, dotGenderFemale, txtGenderFemale, !isMale);
        checkDirty();
    }

    private void applyGenderCard(LinearLayout card, View dot, TextView label, boolean active) {
        card.setBackground(ContextCompat.getDrawable(requireContext(),
                active ? R.drawable.bg_gender_card_active : R.drawable.bg_gender_card_inactive));

        int dotColor = active ? Color.parseColor("#F97316") : Color.parseColor("#E7E5E4");
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(dotColor);
        if (active) dotBg.setStroke(dpToPx(3), Color.parseColor("#FED7AA"));
        dot.setBackground(dotBg);

        label.setTextColor(active ? Color.parseColor("#F97316") : Color.parseColor("#44403C"));
        label.setTypeface(label.getTypeface(), active
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    // ── DOB popup ─────────────────────────────────────────────────────────────

    private void showDobPopup(View anchor, TextView valueView, ImageView chevron,
                              String[] items) {
        dismissOpenPopup();

        String currentValue = valueView.getText().toString();
        setDobSegmentActive(anchor, valueView, chevron, true);
        dobBar.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_dob_bar_focused));

        DobPopupAdapter adapter = new DobPopupAdapter(requireContext(), items, currentValue);
        ListPopupWindow popup = new ListPopupWindow(requireContext());
        popup.setAnchorView(anchor);
        popup.setAdapter(adapter);
        popup.setWidth(dpToPx(120));
        popup.setHeight(dpToPx(200));
        popup.setModal(true);
        popup.setBackgroundDrawable(
                ContextCompat.getDrawable(requireContext(), R.drawable.bg_dob_dropdown));
        popup.setOnItemClickListener((parent, view, position, id) -> {
            String chosen = items[position];
            valueView.setText(chosen);
            if (anchor == segDay)        selectedDobDay   = chosen;
            else if (anchor == segMonth) selectedDobMonth = chosen;
            else                         selectedDobYear  = chosen;
            popup.dismiss();
            checkDirty();
        });
        popup.setOnDismissListener(() -> {
            openPopup = null;
            setDobSegmentActive(anchor, valueView, chevron, false);
            dobBar.setBackground(ContextCompat.getDrawable(requireContext(),
                    R.drawable.bg_dob_bar_default));
        });
        popup.show();
        openPopup = popup;

        // Scroll to selected item.
        int idx = Arrays.asList(items).indexOf(currentValue);
        if (idx > 0) popup.getListView().setSelection(Math.max(0, idx - 1));
    }

    private void setDobSegmentActive(View segment, TextView valueView,
                                     ImageView chevron, boolean active) {
        int color = active ? Color.parseColor("#F97316") : Color.parseColor("#1C1917");
        valueView.setTextColor(color);
        chevron.setColorFilter(active ? Color.parseColor("#F97316")
                                      : Color.parseColor("#A8A29E"));
    }

    private void dismissOpenPopup() {
        if (openPopup != null && openPopup.isShowing()) openPopup.dismiss();
        openPopup = null;
    }

    // ── Tag chips ─────────────────────────────────────────────────────────────

    private void populateTagChips(List<ProfileTagMaster> masterTags, List<String> selectedNames) {
        chipGroupTags.removeAllViews();
        for (ProfileTagMaster tag : masterTags) {
            Chip chip = new Chip(requireContext());
            chip.setText(tag.getTagName());
            chip.setTag(tag.getTagId());
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setRippleColorResource(R.color.orange_primary);
            chip.setTextSize(13f);
            chip.setChipMinHeight(dpToPx(36));
            chip.setChipStartPadding(dpToPx(6));
            chip.setChipEndPadding(dpToPx(6));

            boolean isSelected = selectedNames != null && selectedNames.contains(tag.getTagName());
            chip.setChecked(isSelected);
            applyChipStyle(chip, isSelected);

            chip.setOnCheckedChangeListener((btn, checked) -> {
                applyChipStyle(chip, checked);
                checkDirty();
            });
            chipGroupTags.addView(chip);
        }
    }

    private void applyChipStyle(Chip chip, boolean active) {
        if (active) {
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#F97316")));
            chip.setChipStrokeColor(ColorStateList.valueOf(Color.parseColor("#F97316")));
            chip.setChipStrokeWidth(dpToPx(1.5f));
            chip.setTextColor(Color.WHITE);
        } else {
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.WHITE));
            chip.setChipStrokeColor(ColorStateList.valueOf(Color.parseColor("#E7E5E4")));
            chip.setChipStrokeWidth(dpToPx(1.5f));
            chip.setTextColor(Color.parseColor("#44403C"));
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

    private Set<String> collectSelectedTagNames() {
        Set<String> result = new HashSet<>();
        for (int i = 0; i < chipGroupTags.getChildCount(); i++) {
            View child = chipGroupTags.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                result.add(((Chip) child).getText().toString());
            }
        }
        return result;
    }

    // ── Dirty check ───────────────────────────────────────────────────────────

    private void checkDirty() {
        if (origFullName == null) return; // originals not yet loaded

        boolean dirty = false;
        dirty |= !etFullName.getText().toString().trim().equals(origFullName);
        dirty |= !selectedGender.equals(origGender);
        dirty |= !selectedDobDay.equals(origDobDay);
        dirty |= !selectedDobMonth.equals(origDobMonth);
        dirty |= !selectedDobYear.equals(origDobYear);
        dirty |= !etBio.getText().toString().trim().equals(origBio);
        // Skip tag comparison until chips are rendered (avoids false-dirty on first load).
        if (chipGroupTags.getChildCount() > 0) {
            dirty |= !collectSelectedTagNames().equals(origTagNames);
        }

        btnSave.setEnabled(dirty);
        btnSave.setAlpha(dirty ? 1f : 0.5f);
    }

    // ── DOB helpers ───────────────────────────────────────────────────────────

    private void parseDobIntoSegments(String dob) {
        if (dob == null || dob.isEmpty()) return;
        // Expected format: yyyy-MM-dd
        try {
            String[] parts = dob.split("-");
            if (parts.length == 3) {
                selectedDobYear  = parts[0];
                int monthIdx     = Integer.parseInt(parts[1]) - 1;
                selectedDobMonth = (monthIdx >= 0 && monthIdx < MONTHS.length) ? MONTHS[monthIdx] : parts[1];
                selectedDobDay   = String.valueOf(Integer.parseInt(parts[2]));
                // Pad day to match DAYS array format (e.g. "01", "02"...)
                if (selectedDobDay.length() == 1) selectedDobDay = "0" + selectedDobDay;

                txtDobDay.setText(selectedDobDay);
                txtDobMonth.setText(selectedDobMonth);
                txtDobYear.setText(selectedDobYear);
            }
        } catch (Exception ignored) {}
    }

    private String[] parseDobToArray(String dob) {
        String day = "", month = "", year = "";
        if (dob != null && !dob.isEmpty()) {
            try {
                String[] parts = dob.split("-");
                if (parts.length == 3) {
                    year  = parts[0];
                    int m = Integer.parseInt(parts[1]) - 1;
                    month = (m >= 0 && m < MONTHS.length) ? MONTHS[m] : parts[1];
                    day   = String.valueOf(Integer.parseInt(parts[2]));
                    if (day.length() == 1) day = "0" + day;
                }
            } catch (Exception ignored) {}
        }
        return new String[]{day, month, year};
    }

    private String buildDobString() {
        if (selectedDobDay.isEmpty() || selectedDobMonth.isEmpty() || selectedDobYear.isEmpty())
            return null;
        int monthIdx = Arrays.asList(MONTHS).indexOf(selectedDobMonth) + 1;
        String mm = monthIdx < 10 ? "0" + monthIdx : String.valueOf(monthIdx);
        return selectedDobYear + "-" + mm + "-" + selectedDobDay;
    }

    // ── Image picker ─────────────────────────────────────────────────────────

    private void launchImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    // ── Util ─────────────────────────────────────────────────────────────────

    private int dpToPx(float dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }

    private static String[] buildDays() {
        String[] days = new String[31];
        for (int i = 0; i < 31; i++) days[i] = String.format("%02d", i + 1);
        return days;
    }

    private static String[] buildYears() {
        String[] years = new String[75];
        for (int i = 0; i < 75; i++) years[i] = String.valueOf(2024 - i);
        return years;
    }

    // ── Inner: SimpleTextWatcher ──────────────────────────────────────────────

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    // ── Inner: DobPopupAdapter ────────────────────────────────────────────────

    private static class DobPopupAdapter extends ArrayAdapter<String> {
        private final String selectedValue;

        DobPopupAdapter(android.content.Context ctx, String[] items, String selected) {
            super(ctx, 0, items);
            this.selectedValue = selected;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_dob_option, parent, false);
            }
            String item     = getItem(position);
            boolean isSelected = item != null && item.equals(selectedValue);

            TextView txt = convertView.findViewById(R.id.txtDobOption);
            View dot     = convertView.findViewById(R.id.dobDot);
            txt.setText(item);

            if (isSelected) {
                convertView.setBackgroundColor(Color.parseColor("#FFF7ED"));
                txt.setTextColor(Color.parseColor("#F97316"));
                txt.setTypeface(txt.getTypeface(), android.graphics.Typeface.BOLD);
                dot.setVisibility(View.VISIBLE);
            } else {
                convertView.setBackgroundColor(Color.TRANSPARENT);
                txt.setTextColor(Color.parseColor("#44403C"));
                txt.setTypeface(android.graphics.Typeface.DEFAULT);
                dot.setVisibility(View.GONE);
            }
            return convertView;
        }
    }
}
