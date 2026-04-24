package com.walkmate.ui.explore;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.walkmate.R;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Onboarding gate bottom sheet.
 *
 * Shown by ExploreFragment when the backend returns PROFILE_INCOMPLETE_FOR_MATCHING
 * (HTTP 403). The user must select a gender and at least one interest tag before
 * the intent creation flow can proceed.
 *
 * Lifecycle:
 *   1. {@link #newInstance} is called with the UserProfileRepository.
 *   2. On show, fetches the current profile silently so existing fields (fullName,
 *      dateOfBirth, etc.) are preserved in the update call.
 *   3. User picks gender + tags and taps "Save & Continue".
 *   4. Calls {@link UserProfileRepository#updateProfile} with the merged payload.
 *   5. On success, invokes {@link OnCompleteListener#onOnboardingComplete()}.
 *      ExploreFragment re-submits the intent creation form.
 */
public class OnboardingBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "OnboardingBottomSheet";

    /** Default interest tags exposed by the backend onboarding gate. */
    public static final List<String> DEFAULT_TAGS = Arrays.asList(
            "Strolling",
            "Power Walking",
            "Talkative",
            "Scenic & Quiet",
            "Pet Friendly"
    );

    /** Callback fired after a successful profile update. */
    public interface OnCompleteListener {
        void onOnboardingComplete();
    }

    // ── Dependencies (set before show()) ─────────────────────────────────────

    private UserProfileRepository profileRepository;
    private OnCompleteListener onCompleteListener;

    // ── Cached profile fields (preserves values the user already set) ─────────

    private UserProfile cachedProfile;

    // ── Views ─────────────────────────────────────────────────────────────────

    private ChipGroup chipGroupGender;
    private ChipGroup chipGroupTags;
    private Button    btnSave;
    private View      progressBar;
    private TextView  txtError;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static OnboardingBottomSheet newInstance(UserProfileRepository repository) {
        OnboardingBottomSheet sheet = new OnboardingBottomSheet();
        sheet.profileRepository = repository;
        return sheet;
    }

    public void setOnCompleteListener(OnCompleteListener listener) {
        this.onCompleteListener = listener;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_onboarding, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        chipGroupGender = view.findViewById(R.id.chipGroupGender);
        chipGroupTags   = view.findViewById(R.id.chipGroupTags);
        btnSave         = view.findViewById(R.id.btnSaveOnboarding);
        progressBar     = view.findViewById(R.id.progressOnboarding);
        txtError        = view.findViewById(R.id.txtOnboardingError);

        btnSave.setEnabled(false);
        setLoading(true);

        // Fetch current profile so we preserve existing fullName, DOB, bio, etc.
        profileRepository.getMyProfile(new DomainCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                requireActivity().runOnUiThread(() -> {
                    cachedProfile = profile;
                    setLoading(false);
                    btnSave.setEnabled(true);
                });
            }

            @Override
            public void onError(Exception e) {
                // Non-fatal: proceed with null cache; update will use empty defaults.
                requireActivity().runOnUiThread(() -> {
                    setLoading(false);
                    btnSave.setEnabled(true);
                });
            }
        });

        btnSave.setOnClickListener(v -> onSaveClicked());
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void onSaveClicked() {
        String gender = selectedGender();
        List<String> tags = selectedTags();

        txtError.setVisibility(View.GONE);

        if (gender == null) {
            txtError.setText(getString(R.string.onboarding_error_gender_required));
            txtError.setVisibility(View.VISIBLE);
            return;
        }
        if (tags.isEmpty()) {
            txtError.setText(getString(R.string.onboarding_error_tags_required));
            txtError.setVisibility(View.VISIBLE);
            return;
        }

        // Merge with the cached profile so no pre-existing data is overwritten.
        String fullName    = cachedProfile != null && cachedProfile.getFullName() != null
                ? cachedProfile.getFullName() : "";
        String dob         = cachedProfile != null ? cachedProfile.getDateOfBirth() : null;
        String bio         = cachedProfile != null ? cachedProfile.getBio() : null;
        int    radius      = cachedProfile != null ? cachedProfile.getSearchRadius() : 5000;

        setLoading(true);
        profileRepository.updateProfile(fullName, gender, dob, bio, radius, tags,
                new DomainCallback<UserProfile>() {
                    @Override
                    public void onSuccess(UserProfile profile) {
                        requireActivity().runOnUiThread(() -> {
                            dismiss();
                            if (onCompleteListener != null) {
                                onCompleteListener.onOnboardingComplete();
                            }
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        requireActivity().runOnUiThread(() -> {
                            setLoading(false);
                            String msg = e.getMessage();
                            txtError.setText(msg != null ? msg
                                    : getString(R.string.onboarding_error_save_failed));
                            txtError.setVisibility(View.VISIBLE);
                        });
                    }
                });
    }

    @Nullable
    private String selectedGender() {
        int checkedId = chipGroupGender.getCheckedChipId();
        if (checkedId == View.NO_ID) return null;
        Chip chip = chipGroupGender.findViewById(checkedId);
        if (chip == null) return null;
        // Map display label → backend value ('z' for ANY as specified).
        switch (chip.getText().toString()) {
            case "Male":   return "MALE";
            case "Female": return "FEMALE";
            default:       return "ANY";
        }
    }

    private List<String> selectedTags() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < chipGroupTags.getChildCount(); i++) {
            View child = chipGroupTags.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                result.add(((Chip) child).getText().toString());
            }
        }
        return result;
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnSave != null)     btnSave.setEnabled(!loading);
    }
}
