package com.walkmate.ui.login;

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
import com.walkmate.ui.main.MainActivity;
import com.walkmate.ui.register.RegisterActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private ImageView ivTogglePassword;
    private AppCompatButton btnSignInAction;
    private boolean isPasswordVisible = false;

    private LoginViewModel loginViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        // Initialize ViewModel via Factory (Dependency Injection point)
        LoginViewModelFactory factory = new LoginViewModelFactory(this);
        loginViewModel = new ViewModelProvider(this, factory).get(LoginViewModel.class);

        initViews();
        initWindowInsets();
        initClickListeners();
        setupPasswordToggle();

        // MVVM Core: Bind UI to State Observer
        observeUiState();
    }

    private void initViews() {
        etEmail = findViewById(R.id.et_email_login);
        etPassword = findViewById(R.id.et_password_login);
        ivTogglePassword = findViewById(R.id.iv_toggle_password);
        btnSignInAction = findViewById(R.id.btn_signin_action);
    }

    private void initWindowInsets() {
        Window window = getWindow();
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        window.setStatusBarColor(Color.TRANSPARENT);

        View mainView = findViewById(R.id.login);
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
        AppCompatButton btnTabSignUp = findViewById(R.id.btn_tab_signup);
        TextView tvFooterSignUp = findViewById(R.id.tv_footer_signup);

        View.OnClickListener goToRegister = v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        };

        if (btnTabSignUp != null)
            btnTabSignUp.setOnClickListener(goToRegister);
        if (tvFooterSignUp != null)
            tvFooterSignUp.setOnClickListener(goToRegister);

        // Submit action simply forwards UI Event to ViewModel
        if (btnSignInAction != null) {
            btnSignInAction.setOnClickListener(v -> {
                String email = etEmail.getText().toString();
                String password = etPassword.getText().toString();
                loginViewModel.login(email, password);
            });
        }
    }

    private void observeUiState() {
        loginViewModel.getUiState().observe(this, state -> {
            if (state == null)
                return;

            // 1. Reactive Loading State
            if (btnSignInAction != null) {
                btnSignInAction.setEnabled(!state.isLoading());
                btnSignInAction.setText(state.isLoading() ? "Signing in..." : "Sign In ✦");
            }

            // 2. Reactive Error State (One-time Effect handling)
            if (state.getError() != null) {
                Toast.makeText(LoginActivity.this, state.getError(), Toast.LENGTH_LONG).show();
                loginViewModel.consumeError();
            }

            // 3. Reactive Success Navigation
            if (state.isSuccess()) {
                navigateToMain();
            }
        });
    }

    private void navigateToMain() {
        Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
