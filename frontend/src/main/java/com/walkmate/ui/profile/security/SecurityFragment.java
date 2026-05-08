package com.walkmate.ui.profile.security;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.R;
import com.walkmate.core.designsystem.view.WalkMateButton;
import com.walkmate.core.designsystem.view.WalkMateInputField;

public class SecurityFragment extends Fragment {

    private TextView         txtPasswordStatus;
    private TextView         txtGoogleStatus;
    private WalkMateInputField fieldCurrentPassword;
    private WalkMateInputField fieldNewPassword;
    private WalkMateInputField fieldConfirmPassword;
    private WalkMateButton   btnSavePassword;
    private View             rowCurrentPassword;

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

        txtPasswordStatus    = view.findViewById(R.id.txtPasswordStatus);
        txtGoogleStatus      = view.findViewById(R.id.txtGoogleStatus);
        fieldCurrentPassword = view.findViewById(R.id.fieldCurrentPassword);
        fieldNewPassword     = view.findViewById(R.id.fieldNewPassword);
        fieldConfirmPassword = view.findViewById(R.id.fieldConfirmPassword);
        btnSavePassword      = view.findViewById(R.id.btnSavePassword);
        rowCurrentPassword   = view.findViewById(R.id.rowCurrentPassword);

        view.findViewById(R.id.btnBackSecurity).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

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

        txtPasswordStatus.setText(state.hasPassword()
                ? "Password: Set"
                : "Password: Not Set");

        txtGoogleStatus.setText(state.hasGoogle()
                ? "Google: Connected"
                : "Google: Not Connected");

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

    private void clearFields() {
        fieldCurrentPassword.setText("");
        fieldNewPassword.setText("");
        fieldConfirmPassword.setText("");
    }
}
