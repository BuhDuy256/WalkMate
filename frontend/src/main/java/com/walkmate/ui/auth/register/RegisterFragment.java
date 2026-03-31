package com.walkmate.ui.auth.register;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.R;
import com.walkmate.core.designsystem.view.WalkMateButton;
import com.walkmate.core.designsystem.view.WalkMateInputField;
import com.walkmate.ui.auth.AuthActivity;

public class RegisterFragment extends Fragment {

    private WalkMateInputField fieldFullName;
    private WalkMateInputField fieldEmail;
    private WalkMateInputField fieldPassword;
    private WalkMateButton btnRegister;

    private RegisterViewModel registerViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register_form, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RegisterViewModelFactory factory = new RegisterViewModelFactory(requireContext());
        registerViewModel = new ViewModelProvider(this, factory).get(RegisterViewModel.class);

        initViews(view);
        initClickListeners(view);
        observeUiState();
    }

    private void initViews(View root) {
        fieldFullName = root.findViewById(R.id.field_fullname);
        fieldEmail    = root.findViewById(R.id.field_email);
        fieldPassword = root.findViewById(R.id.field_password);
        btnRegister   = root.findViewById(R.id.btn_register_action);
    }

    private void initClickListeners(View root) {
        AppCompatButton btnTabSignIn = root.findViewById(R.id.btn_tab_signin_reg);
        TextView tvFooterSignIn = root.findViewById(R.id.tv_footer_signin);

        View.OnClickListener switchToLogin = v -> {
            if (getActivity() instanceof AuthActivity) {
                ((AuthActivity) getActivity()).showLoginTab();
            }
        };

        if (btnTabSignIn != null) btnTabSignIn.setOnClickListener(switchToLogin);
        if (tvFooterSignIn != null) tvFooterSignIn.setOnClickListener(switchToLogin);

        btnRegister.setOnClickListener(v ->
                registerViewModel.register(
                        fieldFullName.getText(), fieldEmail.getText(), fieldPassword.getText()));
    }

    private void observeUiState() {
        registerViewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;

            btnRegister.setLoading(state.isLoading());

            fieldFullName.setError(state.getFullNameError());
            fieldEmail.setError(state.getEmailError());
            fieldPassword.setError(state.getPasswordError());

            if (state.getError() != null) {
                Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_LONG).show();
                registerViewModel.consumeError();
            }

            if (state.isSuccess() && getActivity() instanceof AuthActivity) {
                ((AuthActivity) getActivity()).onRegisterSuccess();
            }
        });
    }
}
