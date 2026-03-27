package com.walkmate.ui.auth.login;

import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.R;
import com.walkmate.ui.auth.AuthActivity;

public class LoginFragment extends Fragment {

    private EditText etEmail;
    private EditText etPassword;
    private ImageView ivTogglePassword;
    private AppCompatButton btnSignInAction;
    private boolean isPasswordVisible = false;

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
        setupPasswordToggle();
        observeUiState();
    }

    private void initViews(View root) {
        etEmail = root.findViewById(R.id.et_email_login);
        etPassword = root.findViewById(R.id.et_password_login);
        ivTogglePassword = root.findViewById(R.id.iv_toggle_password);
        btnSignInAction = root.findViewById(R.id.btn_signin_action);
    }

    private void setupPasswordToggle() {
        if (ivTogglePassword != null) {
            ivTogglePassword.setOnClickListener(v -> {
                isPasswordVisible = !isPasswordVisible;
                if (isPasswordVisible) {
                    etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                    ivTogglePassword.setImageResource(R.drawable.ic_eye_show);
                } else {
                    etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                    ivTogglePassword.setImageResource(R.drawable.ic_eye_hide);
                }
                etPassword.setSelection(etPassword.getText().length());
            });
        }
    }

    private void initClickListeners(View root) {
        AppCompatButton btnTabSignUp = root.findViewById(R.id.btn_tab_signup);
        TextView tvFooterSignUp = root.findViewById(R.id.tv_footer_signup);

        View.OnClickListener switchToRegister = v -> {
            if (getActivity() instanceof AuthActivity) {
                ((AuthActivity) getActivity()).showRegisterTab();
            }
        };

        if (btnTabSignUp != null) {
            btnTabSignUp.setOnClickListener(switchToRegister);
        }
        if (tvFooterSignUp != null) {
            tvFooterSignUp.setOnClickListener(switchToRegister);
        }

        if (btnSignInAction != null) {
            btnSignInAction.setOnClickListener(v -> {
                String email = etEmail.getText().toString();
                String password = etPassword.getText().toString();
                loginViewModel.login(email, password);
            });
        }
    }

    private void observeUiState() {
        loginViewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }

            if (btnSignInAction != null) {
                btnSignInAction.setEnabled(!state.isLoading());
                btnSignInAction.setText(state.isLoading() ? "Signing in..." : "Sign In ✦");
            }

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
