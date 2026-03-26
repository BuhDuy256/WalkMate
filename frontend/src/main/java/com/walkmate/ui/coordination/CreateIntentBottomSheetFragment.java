package com.walkmate.ui.coordination;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.RangeSlider;
import com.walkmate.R;

import java.util.List;
import java.util.Locale;

/**
 * Phase 1: Create Intent Bottom Sheet.
 * Extracted from the "God Layout" Layer 4.
 * Only inflated when the user taps "Set Walking Intent".
 */
public class CreateIntentBottomSheetFragment extends BottomSheetDialogFragment {

    public interface OnIntentActionListener {
        void onFindMatchClicked();
        void onSheetDismissed();
    }

    private OnIntentActionListener listener;

    public void setOnIntentActionListener(OnIntentActionListener listener) {
        this.listener = listener;
    }

    @Override
    public int getTheme() {
        return R.style.Theme_WalkMate_BottomSheet;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            View bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                // Expand to 2/3 of screen
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                behavior.setPeekHeight(screenHeight * 2 / 3);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                // Transparent background so our rounded corners show
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_create_intent, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Close button
        View btnCloseSheet = view.findViewById(R.id.btnCloseSheet);
        btnCloseSheet.setOnClickListener(v -> dismiss());

        // Find Match button
        MaterialButton btnFindMatch = view.findViewById(R.id.btnFindMatch);
        btnFindMatch.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFindMatchClicked();
            }
            dismiss();
        });

        // Sliders
        setupSliders(view);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listener != null) {
            listener.onSheetDismissed();
        }
    }

    private void setupSliders(View view) {
        RangeSlider sliderTime = view.findViewById(R.id.sliderTime);
        RangeSlider sliderAge = view.findViewById(R.id.sliderAge);
        TextView txtTimeStart = view.findViewById(R.id.txtTimeStart);
        TextView txtTimeEnd = view.findViewById(R.id.txtTimeEnd);
        TextView txtAgeMin = view.findViewById(R.id.txtAgeMin);
        TextView txtAgeMax = view.findViewById(R.id.txtAgeMax);

        sliderTime.setValues(16f, 22f);
        sliderTime.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> values = slider.getValues();
            txtTimeStart.setText(formatTime(values.get(0)));
            txtTimeEnd.setText(formatTime(values.get(1)));
        });

        sliderAge.setValues(18f, 40f);
        sliderAge.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> values = slider.getValues();
            txtAgeMin.setText(String.valueOf(Math.round(values.get(0))));
            txtAgeMax.setText(String.valueOf(Math.round(values.get(1))));
        });
    }

    private String formatTime(float val) {
        int h = (int) Math.floor(val);
        int m = (int) ((val % 1) * 60);
        return String.format(Locale.getDefault(), "%02d:%02d", h, m);
    }
}
