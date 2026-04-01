package com.walkmate.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.R;
import com.walkmate.core.designsystem.view.WalkMateButton;
import com.walkmate.core.designsystem.view.WalkMateInputField;
import com.walkmate.ui.auth.login.LoginViewModel;
import com.walkmate.ui.auth.login.LoginViewModelFactory;
import com.walkmate.ui.auth.register.RegisterActivity;

public class AuthActivity extends AppCompatActivity {

    private WalkMateInputField fieldEmail;
    private WalkMateInputField fieldPassword;
    private WalkMateButton btnSignIn;

    private LoginViewModel loginViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });
    }
}
