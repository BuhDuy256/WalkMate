package com.walkmate.ui.explore;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.walkmate.R;

/**
 * Shown when the user attempts to create an intent but their profile is incomplete
 * (missing age or walk tags). Replaces the old Toast + navigate-to-editProfile
 * behaviour with a proper bottom sheet prompt.
 *
 * The host (ExploreFragment) provides the action callback so that navigation
 * happens through the NavController owned by the correct NavHostFragment.
 */
public class IncompleteProfileBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "IncompleteProfileBottomSheet";

    public interface OnCompleteProfileListener {
        void onCompleteProfileRequested();
    }

    private OnCompleteProfileListener listener;

    public static IncompleteProfileBottomSheet newInstance() {
        return new IncompleteProfileBottomSheet();
    }

    public void setOnCompleteProfileListener(OnCompleteProfileListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_incomplete_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btnCompleteProfile).setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onCompleteProfileRequested();
            }
        });

        view.findViewById(R.id.btnDismissIncomplete).setOnClickListener(v -> dismiss());
    }
}
