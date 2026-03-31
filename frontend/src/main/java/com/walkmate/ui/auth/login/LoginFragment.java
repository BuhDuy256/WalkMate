package com.walkmate.ui.auth.login;

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

public class LoginFragment extends Fragment {

    private WalkMateInputField fieldEmail;
    private WalkMateInputField fieldPassword;
    private WalkMateButton btnSignIn;

    private LoginViewModel loginViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login_form, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LoginViewModelFactory factory = new LoginViewModelFactory(requireContext());
        loginViewModel = new ViewModelProvider(this, factory).get(LoginViewModel.class);

        initViews(view);
        initClickListeners(view);
        observeUiState();
    }

    private void initViews(View root) {
        fieldEmail    = root.findViewById(R.id.field_email);
        fieldPassword = root.findViewById(R.id.field_password);
        btnSignIn     = root.findViewById(R.id.btn_signin_action);
    }

    private void initClickListeners(View root) {
        AppCompatButton btnTabSignUp = root.findViewById(R.id.btn_tab_signup);
        TextView tvFooterSignUp = root.findViewById(R.id.tv_footer_signup);

        View.OnClickListener switchToRegister = v -> {
            if (getActivity() instanceof AuthActivity) {
                ((AuthActivity) getActivity()).showRegisterTab();
            }
        };

        if (btnTabSignUp != null) btnTabSignUp.setOnClickListener(switchToRegister);
        if (tvFooterSignUp != null) tvFooterSignUp.setOnClickListener(switchToRegister);

        btnSignIn.setOnClickListener(v ->
                loginViewModel.login(fieldEmail.getText(), fieldPassword.getText()));
    }

    private void observeUiState() {
        loginViewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;

            btnSignIn.setLoading(state.isLoading());

            if (state.getError() != null) {
                Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_LONG).show();
                loginViewModel.consumeError();
            }

            if (state.isSuccess() && getActivity() instanceof AuthActivity) {
                ((AuthActivity) getActivity()).onLoginSuccess();
            }
        });
    }
}
