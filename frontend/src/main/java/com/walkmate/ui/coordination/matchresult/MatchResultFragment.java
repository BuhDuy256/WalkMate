package com.walkmate.ui.coordination.matchresult;

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
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.walkmate.R;

/**
 * Phase 3: Match Result Dialog.
 * MatchResultViewModel owns the accept/pass action state.
 * Fragment observes and fires the callback once an action is taken.
 */
public class MatchResultFragment extends DialogFragment {

    public interface OnMatchResultActionListener {
        void onAcceptClicked();
        void onPassClicked();
    }

    private OnMatchResultActionListener listener;
    private MatchResultViewModel viewModel;

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

        viewModel = new ViewModelProvider(this).get(MatchResultViewModel.class);

        ImageView imgMatchAvatar = view.findViewById(R.id.imgMatchAvatar);
        imgMatchAvatar.setImageResource(R.drawable.bg_warm_circle);

        MaterialButton btnAccept = view.findViewById(R.id.btnAccept);
        MaterialButton btnPass   = view.findViewById(R.id.btnPass);

        btnAccept.setOnClickListener(v -> viewModel.accept());
        btnPass.setOnClickListener(v -> viewModel.pass());

        observeState();
    }

    private void observeState() {
        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            switch (state.getAction()) {
                case ACCEPTED:
                    if (listener != null) listener.onAcceptClicked();
                    dismiss();
                    break;
                case PASSED:
                    if (listener != null) listener.onPassClicked();
                    dismiss();
                    break;
                default:
                    break;
            }
        });
    }
}
