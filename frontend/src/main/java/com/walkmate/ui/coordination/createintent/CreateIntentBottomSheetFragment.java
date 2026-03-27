package com.walkmate.ui.coordination.createintent;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.RangeSlider;
import com.walkmate.R;
import com.walkmate.domain.walkintent.WalkIntent;

import java.util.List;
import java.util.Locale;

/**
 * Phase 1: Create Intent Bottom Sheet.
 * Owns its ViewModel (CreateIntentViewModel) for form submission logic.
 * Notifies CoordinationActivity via OnIntentActionListener when intent is created.
 */
public class CreateIntentBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_HOTSPOT_ID = "hotspot_id";

    public interface OnIntentActionListener {
        void onIntentCreated(WalkIntent intent);
        void onSheetDismissed();
    }

    private OnIntentActionListener listener;
    private CreateIntentViewModel viewModel;

    private RangeSlider sliderTime;
    private RangeSlider sliderAge;
    private MaterialButton btnFindMatch;

    public static CreateIntentBottomSheetFragment newInstance(String hotspotId) {
        CreateIntentBottomSheetFragment fragment = new CreateIntentBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_HOTSPOT_ID, hotspotId);
        fragment.setArguments(args);
        return fragment;
    }

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
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                behavior.setPeekHeight(screenHeight * 2 / 3);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
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

        viewModel = new ViewModelProvider(this, new CreateIntentViewModelFactory())
                .get(CreateIntentViewModel.class);

        btnFindMatch = view.findViewById(R.id.btnFindMatch);
        setupSliders(view);
        setupListeners();
        observeState();
    }

    private void setupSliders(View view) {
        sliderTime = view.findViewById(R.id.sliderTime);
        sliderAge = view.findViewById(R.id.sliderAge);
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

    private void setupListeners() {
        requireView().findViewById(R.id.btnCloseSheet).setOnClickListener(v -> dismiss());

        btnFindMatch.setOnClickListener(v -> {
            String hotspotId = getArguments() != null
                    ? getArguments().getString(ARG_HOTSPOT_ID, "") : "";
            float timeStart = sliderTime.getValues().get(0);
            float timeEnd   = sliderTime.getValues().get(1);
            int ageMin      = Math.round(sliderAge.getValues().get(0));
            int ageMax      = Math.round(sliderAge.getValues().get(1));
            viewModel.submit(hotspotId, timeStart, timeEnd, ageMin, ageMax);
        });
    }

    private void observeState() {
        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            btnFindMatch.setEnabled(!state.isLoading());

            if (state.getSubmittedIntent() != null) {
                if (listener != null) {
                    listener.onIntentCreated(state.getSubmittedIntent());
                }
                dismiss();
                return;
            }

            if (state.getError() != null) {
                Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_SHORT).show();
                viewModel.consumeError();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listener != null) {
            listener.onSheetDismissed();
        }
    }

    private String formatTime(float val) {
        int h = (int) Math.floor(val);
        int m = (int) ((val % 1) * 60);
        return String.format(Locale.getDefault(), "%02d:%02d", h, m);
    }
}
