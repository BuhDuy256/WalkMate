package com.walkmate.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.designsystem.view.WalkMateButton;
import com.walkmate.core.designsystem.view.WalkMateInputField;
import com.walkmate.ui.auth.login.LoginViewModel;
import com.walkmate.ui.auth.login.LoginViewModelFactory;
import com.walkmate.ui.auth.register.RegisterActivity;
import com.walkmate.ui.main.MainActivity;

public class AuthActivity extends AppCompatActivity {

    private WalkMateInputField fieldEmail;
    private WalkMateInputField fieldPassword;
    private WalkMateButton btnSignIn;

    private LoginViewModel loginViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── Login persistence: skip to Home if already authenticated ──
        WalkMateApplication app = (WalkMateApplication) getApplication();
        if (app.getSessionManager().hasUsableAccessToken()) {
            onLoginSuccess();
            return;
        }

        // Stored token exists but is unusable (blank/expired/invalid) — clear it
        // so the app does not repeatedly attempt auto-login with stale credentials.
        if (app.getSessionManager().getAccessToken() != null) {
            app.getSessionManager().clearSession();
        }

        setContentView(R.layout.activity_auth);

        LoginViewModelFactory factory = new LoginViewModelFactory(this);
        loginViewModel = new ViewModelProvider(this, factory).get(LoginViewModel.class);

        initViews();
        initClickListeners();
        observeUiState();
    }

    private void initViews() {
        fieldEmail    = findViewById(R.id.field_email);
        fieldPassword = findViewById(R.id.field_password);
        btnSignIn     = findViewById(R.id.btn_signin_action);
    }

    private void initClickListeners() {
        btnSignIn.setOnClickListener(v ->
                loginViewModel.login(fieldEmail.getText(), fieldPassword.getText()));

        TextView tvForgotPassword = findViewById(R.id.tv_forgot_password);
        tvForgotPassword.setOnClickListener(v -> {
            // TODO: navigate to forgot-password screen
        });

        TextView tvCreateAccount = findViewById(R.id.tv_create_account);
        tvCreateAccount.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void observeUiState() {
        loginViewModel.getUiState().observe(this, state -> {
            if (state == null) return;

            btnSignIn.setLoading(state.isLoading());

            if (state.getError() != null) {
                Toast.makeText(this, state.getError(), Toast.LENGTH_LONG).show();
                loginViewModel.consumeError();
            }

            if (state.isSuccess()) {
                onLoginSuccess();
            }
        });
    }

    /**
     * Launches MainActivity and removes AuthActivity from the back stack entirely.
     *
     * FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK together ensure that:
     * - A new task is started with MainActivity as its root.
     * - The existing task (containing AuthActivity) is cleared.
     * Pressing Back from MainActivity will exit the app, not return to login.
     */
    private void onLoginSuccess() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
