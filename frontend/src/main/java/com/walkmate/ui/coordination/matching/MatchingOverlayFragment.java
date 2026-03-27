package com.walkmate.ui.coordination.matching;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.R;

/**
 * Phase 2: Matching Pulse Overlay.
 * MatchingViewModel drives the 3-second timer (survives config changes).
 * Pulse animation stays in the Fragment as it is pure UI.
 */
public class MatchingOverlayFragment extends DialogFragment {

    private static final String ARG_HOTSPOT_NAME = "hotspot_name";

    public interface OnMatchFoundListener {
        void onMatchTimerComplete();
    }

    private OnMatchFoundListener listener;
    private MatchingViewModel viewModel;
    private AnimatorSet pulseAnimatorSet;

    public static MatchingOverlayFragment newInstance(String hotspotName) {
        MatchingOverlayFragment fragment = new MatchingOverlayFragment();
        Bundle args = new Bundle();
        args.putString(ARG_HOTSPOT_NAME, hotspotName);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnMatchFoundListener(OnMatchFoundListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Theme_WalkMate_TransparentDialog);
        setCancelable(false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_matching_overlay, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String hotspotName = getArguments() != null
                ? getArguments().getString(ARG_HOTSPOT_NAME, "") : "";

        viewModel = new ViewModelProvider(this, new MatchingViewModelFactory(hotspotName))
                .get(MatchingViewModel.class);

        TextView txtScanning = view.findViewById(R.id.txtScanning);
        txtScanning.setText(String.format(getString(R.string.scanning_format), hotspotName));

        startPulseAnimation(view);
        observeState();
    }

    private void observeState() {
        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if (state.isMatchFound()) {
                if (listener != null) {
                    listener.onMatchTimerComplete();
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopPulseAnimation();
    }

    private void startPulseAnimation(View root) {
        View pulseRingOuter = root.findViewById(R.id.pulseRingOuter);
        View pulseRingInner = root.findViewById(R.id.pulseRingInner);

        ObjectAnimator outerScaleX = ObjectAnimator.ofFloat(pulseRingOuter, "scaleX", 1f, 1.6f, 1f);
        ObjectAnimator outerScaleY = ObjectAnimator.ofFloat(pulseRingOuter, "scaleY", 1f, 1.6f, 1f);
        ObjectAnimator outerAlpha  = ObjectAnimator.ofFloat(pulseRingOuter, "alpha",  0.6f, 0f, 0.6f);
        outerScaleX.setRepeatCount(ObjectAnimator.INFINITE);
        outerScaleY.setRepeatCount(ObjectAnimator.INFINITE);
        outerAlpha.setRepeatCount(ObjectAnimator.INFINITE);
        outerScaleX.setDuration(1500);
        outerScaleY.setDuration(1500);
        outerAlpha.setDuration(1500);

        ObjectAnimator innerScaleX = ObjectAnimator.ofFloat(pulseRingInner, "scaleX", 1f, 1.3f, 1f);
        ObjectAnimator innerScaleY = ObjectAnimator.ofFloat(pulseRingInner, "scaleY", 1f, 1.3f, 1f);
        ObjectAnimator innerAlpha  = ObjectAnimator.ofFloat(pulseRingInner, "alpha",  0.8f, 0.2f, 0.8f);
        innerScaleX.setRepeatCount(ObjectAnimator.INFINITE);
        innerScaleY.setRepeatCount(ObjectAnimator.INFINITE);
        innerAlpha.setRepeatCount(ObjectAnimator.INFINITE);
        innerScaleX.setDuration(1500);
        innerScaleY.setDuration(1500);
        innerAlpha.setDuration(1500);
        innerScaleX.setStartDelay(300);
        innerScaleY.setStartDelay(300);
        innerAlpha.setStartDelay(300);

        pulseAnimatorSet = new AnimatorSet();
        pulseAnimatorSet.playTogether(
                outerScaleX, outerScaleY, outerAlpha,
                innerScaleX, innerScaleY, innerAlpha);
        pulseAnimatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimatorSet.start();
    }

    private void stopPulseAnimation() {
        if (pulseAnimatorSet != null) {
            pulseAnimatorSet.cancel();
            pulseAnimatorSet = null;
        }
    }
}
