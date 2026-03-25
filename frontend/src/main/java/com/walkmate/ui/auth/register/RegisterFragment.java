package com.walkmate.ui.auth.register;

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

public class RegisterFragment extends Fragment {

    private EditText etFullName;
    private EditText etEmail;
    private EditText etPassword;
    private ImageView ivTogglePassword;
    private AppCompatButton btnRegisterAction;
    private boolean isPasswordVisible = false;

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
        setupPasswordToggle();
        observeUiState();
    }

    private void initViews(View root) {
        etFullName = root.findViewById(R.id.et_fullname);
        etEmail = root.findViewById(R.id.et_email);
        etPassword = root.findViewById(R.id.et_password);
        ivTogglePassword = root.findViewById(R.id.iv_toggle_password_reg);
        btnRegisterAction = root.findViewById(R.id.btn_register_action);
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
        AppCompatButton btnTabSignIn = root.findViewById(R.id.btn_tab_signin_reg);
        TextView tvFooterSignIn = root.findViewById(R.id.tv_footer_signin);

        View.OnClickListener switchToLogin = v -> {
            if (getActivity() instanceof AuthActivity) {
                ((AuthActivity) getActivity()).showLoginTab();
            }
        };

        if (btnTabSignIn != null) {
            btnTabSignIn.setOnClickListener(switchToLogin);
        }
        if (tvFooterSignIn != null) {
            tvFooterSignIn.setOnClickListener(switchToLogin);
        }

        if (btnRegisterAction != null) {
            btnRegisterAction.setOnClickListener(v -> {
                String fullName = etFullName.getText().toString();
                String email = etEmail.getText().toString();
                String password = etPassword.getText().toString();
                registerViewModel.register(fullName, email, password);
            });
        }
    }

    private void observeUiState() {
        registerViewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }

            if (btnRegisterAction != null) {
                btnRegisterAction.setEnabled(!state.isLoading());
                btnRegisterAction.setText(state.isLoading() ? "Creating..." : "Create Account ✦");
            }

            if (state.getFullNameError() != null) {
                etFullName.setError(state.getFullNameError());
            }
            if (state.getEmailError() != null) {
                etEmail.setError(state.getEmailError());
            }
            if (state.getPasswordError() != null) {
                etPassword.setError(state.getPasswordError());
            }

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
