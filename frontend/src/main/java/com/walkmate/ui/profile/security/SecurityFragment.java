package com.walkmate.ui.profile.security;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.R;
import com.walkmate.core.designsystem.view.WalkMateButton;
import com.walkmate.core.designsystem.view.WalkMateInputField;
import com.walkmate.core.util.WindowInsetUtils;

public class SecurityFragment extends Fragment {

    private TextView           txtPasswordStatusPill;
    private TextView           txtGoogleStatusPill;
    private WalkMateInputField fieldCurrentPassword;
    private WalkMateInputField fieldNewPassword;
    private WalkMateInputField fieldConfirmPassword;
    private WalkMateButton     btnSavePassword;
    private View               rowCurrentPassword;
    private View               rowStrengthBar;
    private View               strengthBar1;
    private View               strengthBar2;
    private View               strengthBar3;
    private TextView           txtStrengthLabel;

    private SecurityViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_security, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        WindowInsetUtils.applyStatusBarPadding(view.findViewById(R.id.subPageHeader));
        ((TextView) view.findViewById(R.id.txtSubPageTitle))
                .setText(R.string.security_screen_title);
        view.findViewById(R.id.btnSubPageBack).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        txtPasswordStatusPill = view.findViewById(R.id.txtPasswordStatusPill);
        txtGoogleStatusPill   = view.findViewById(R.id.txtGoogleStatusPill);
        fieldCurrentPassword  = view.findViewById(R.id.fieldCurrentPassword);
        fieldNewPassword      = view.findViewById(R.id.fieldNewPassword);
        fieldConfirmPassword  = view.findViewById(R.id.fieldConfirmPassword);
        btnSavePassword       = view.findViewById(R.id.btnSavePassword);
        rowCurrentPassword    = view.findViewById(R.id.rowCurrentPassword);
        rowStrengthBar        = view.findViewById(R.id.rowStrengthBar);
        strengthBar1          = view.findViewById(R.id.strengthBar1);
        strengthBar2          = view.findViewById(R.id.strengthBar2);
        strengthBar3          = view.findViewById(R.id.strengthBar3);
        txtStrengthLabel      = view.findViewById(R.id.txtStrengthLabel);

        fieldNewPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateStrengthBar(s.toString());
            }
        });

        viewModel = new ViewModelProvider(this, new SecurityViewModelFactory(requireContext()))
                .get(SecurityViewModel.class);

        btnSavePassword.setOnClickListener(v -> {
            String current = fieldCurrentPassword.getText();
            String newPass  = fieldNewPassword.getText();
            String confirm  = fieldConfirmPassword.getText();
            viewModel.setOrChangePassword(current, newPass, confirm);
        });

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
    }

    private void renderState(SecurityUiState state) {
        btnSavePassword.setLoading(state.isLoading());

        if (state.hasPassword()) {
            txtPasswordStatusPill.setText(R.string.security_password_status_set);
            txtPasswordStatusPill.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.orange_primary));
            txtPasswordStatusPill.setBackgroundResource(R.drawable.bg_security_status_orange);
        } else {
            txtPasswordStatusPill.setText(R.string.security_password_status_not_set);
            txtPasswordStatusPill.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.text_muted));
            txtPasswordStatusPill.setBackgroundResource(R.drawable.bg_security_status_gray);
        }

        if (state.hasGoogle()) {
            txtGoogleStatusPill.setText(R.string.security_google_status_connected);
            txtGoogleStatusPill.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.color_confirmed));
            txtGoogleStatusPill.setBackgroundResource(R.drawable.bg_security_status_green);
        } else {
            txtGoogleStatusPill.setText(R.string.security_google_status_not_connected);
            txtGoogleStatusPill.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.text_muted));
            txtGoogleStatusPill.setBackgroundResource(R.drawable.bg_security_status_gray);
        }

        btnSavePassword.setText(state.hasPassword()
                ? getString(R.string.security_btn_change_password)
                : getString(R.string.security_btn_set_password));

        rowCurrentPassword.setVisibility(state.hasPassword() ? View.VISIBLE : View.GONE);

        if (state.isSuccess()) {
            Toast.makeText(requireContext(),
                    getString(R.string.security_password_success), Toast.LENGTH_SHORT).show();
            clearFields();
            viewModel.consumeSuccess();
        }

        if (state.getError() != null) {
            Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_LONG).show();
            viewModel.consumeError();
        }
    }

    private void updateStrengthBar(String password) {
        int len = password.length();
        if (len == 0) {
            rowStrengthBar.setVisibility(View.GONE);
            return;
        }

        rowStrengthBar.setVisibility(View.VISIBLE);
        int strength = len < 6 ? 1 : len < 10 ? 2 : 3;

        int color;
        int labelRes;
        switch (strength) {
            case 1:
                color    = 0xFFEF4444;
                labelRes = R.string.security_strength_weak;
                break;
            case 2:
                color    = 0xFFF59E0B;
                labelRes = R.string.security_strength_good;
                break;
            default:
                color    = 0xFF10B981;
                labelRes = R.string.security_strength_strong;
                break;
        }

        int inactive = ContextCompat.getColor(requireContext(), R.color.card_border);
        strengthBar1.setBackgroundColor(color);
        strengthBar2.setBackgroundColor(strength >= 2 ? color : inactive);
        strengthBar3.setBackgroundColor(strength >= 3 ? color : inactive);
        txtStrengthLabel.setText(labelRes);
        txtStrengthLabel.setTextColor(color);
    }

    private void clearFields() {
        fieldCurrentPassword.setText("");
        fieldNewPassword.setText("");
        fieldConfirmPassword.setText("");
    }
}
