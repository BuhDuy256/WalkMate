package com.walkmate.ui.register;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.R;
import com.walkmate.ui.login.LoginActivity;

public class RegisterActivity extends AppCompatActivity {
    private EditText etFullName;
    private EditText etEmail;
    private EditText etPassword;
    private ImageView ivTogglePassword;
    private AppCompatButton btnRegisterAction;
    private boolean isPasswordVisible = false;

    private RegisterViewModel registerViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

        RegisterViewModelFactory factory = new RegisterViewModelFactory(this);
        registerViewModel = new ViewModelProvider(this, factory).get(RegisterViewModel.class);

        initViews();
        initWindowInsets();
        initClickListeners();
        setupPasswordToggle();

        observeUiState();
    }

    private void initViews() {
        etFullName = findViewById(R.id.et_fullname);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        ivTogglePassword = findViewById(R.id.iv_toggle_password_reg);
        btnRegisterAction = findViewById(R.id.btn_register_action);
    }

    private void initWindowInsets() {
        Window window = getWindow();
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        window.setStatusBarColor(Color.TRANSPARENT);

        View mainView = findViewById(R.id.register);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }
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

    private void initClickListeners() {
        AppCompatButton btnTabSignIn = findViewById(R.id.btn_tab_signin_reg);
        TextView tvFooterSignIn = findViewById(R.id.tv_footer_signin);

        View.OnClickListener goToLogin = v -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        };

        if (btnTabSignIn != null)
            btnTabSignIn.setOnClickListener(goToLogin);
        if (tvFooterSignIn != null)
            tvFooterSignIn.setOnClickListener(goToLogin);

        if (btnRegisterAction != null) {
            btnRegisterAction.setOnClickListener(v -> {
                String fullName = etFullName.getText().toString();
                String email = etEmail.getText().toString();
                String password = etPassword.getText().toString();
                // Send intent to ViewModel
                registerViewModel.register(fullName, email, password);
            });
        }
    }

    private void observeUiState() {
        registerViewModel.getUiState().observe(this, state -> {
            if (state == null)
                return;

            // Handle Loading State
            if (btnRegisterAction != null) {
                btnRegisterAction.setEnabled(!state.isLoading());
                btnRegisterAction.setText(state.isLoading() ? "Creating..." : "Create Account ✦");
            }

            // Handle Field Errors
            if (state.getFullNameError() != null) {
                etFullName.setError(state.getFullNameError());
            }
            if (state.getEmailError() != null) {
                etEmail.setError(state.getEmailError());
            }
            if (state.getPasswordError() != null) {
                etPassword.setError(state.getPasswordError());
            }

            // Handle Backend Action Error
            if (state.getError() != null) {
                Toast.makeText(RegisterActivity.this, state.getError(), Toast.LENGTH_LONG).show();
                registerViewModel.consumeError();
            }

            // Handle Success
            if (state.isSuccess()) {
                Toast.makeText(RegisterActivity.this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                startActivity(intent);
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });
    }
}
