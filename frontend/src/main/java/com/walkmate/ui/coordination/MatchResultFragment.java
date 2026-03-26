package com.walkmate.ui.coordination;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.walkmate.R;

/**
 * Phase 3: Match Result Dialog.
 * Extracted from the "God Layout" Layer 6.
 * Shown as a DialogFragment when a match is found.
 */
public class MatchResultFragment extends DialogFragment {

    public interface OnMatchResultActionListener {
        void onAcceptClicked();
        void onPassClicked();
    }

    private OnMatchResultActionListener listener;

    public void setOnMatchResultActionListener(OnMatchResultActionListener listener) {
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
        return inflater.inflate(R.layout.fragment_match_result, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageView imgMatchAvatar = view.findViewById(R.id.imgMatchAvatar);
        imgMatchAvatar.setImageResource(R.drawable.bg_warm_circle); // placeholder

        MaterialButton btnAccept = view.findViewById(R.id.btnAccept);
        MaterialButton btnPass = view.findViewById(R.id.btnPass);

        btnAccept.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAcceptClicked();
            }
            dismiss();
        });

        btnPass.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPassClicked();
            }
            dismiss();
        });
    }
}
