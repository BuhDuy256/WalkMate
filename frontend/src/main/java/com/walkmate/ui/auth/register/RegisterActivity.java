package com.walkmate.ui.auth.register;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.R;
import com.walkmate.core.designsystem.view.WalkMateButton;
import com.walkmate.core.designsystem.view.WalkMateInputField;

public class RegisterActivity extends AppCompatActivity {

    private WalkMateInputField fieldFullName;
    private WalkMateInputField fieldEmail;
    private WalkMateInputField fieldPassword;
    private WalkMateButton btnRegister;

    private RegisterViewModel registerViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        RegisterViewModelFactory factory = new RegisterViewModelFactory(this);
        registerViewModel = new ViewModelProvider(this, factory).get(RegisterViewModel.class);

        initViews();
        initClickListeners();
        observeUiState();
    }

    private void initViews() {
        fieldFullName = findViewById(R.id.field_fullname);
        fieldEmail    = findViewById(R.id.field_email);
        fieldPassword = findViewById(R.id.field_password);
        btnRegister   = findViewById(R.id.btn_register_action);
    }

    private void initClickListeners() {
        btnRegister.setOnClickListener(v ->
                registerViewModel.register(
                        fieldFullName.getText(), fieldEmail.getText(), fieldPassword.getText()));

        TextView tvSignInLink = findViewById(R.id.tv_signin_link);
        tvSignInLink.setOnClickListener(v -> finish());
    }

    private void observeUiState() {
        registerViewModel.getUiState().observe(this, state -> {
            if (state == null) return;

            btnRegister.setLoading(state.isLoading());

            fieldFullName.setError(state.getFullNameError());
            fieldEmail.setError(state.getEmailError());
            fieldPassword.setError(state.getPasswordError());

            if (state.getError() != null) {
                Toast.makeText(this, state.getError(), Toast.LENGTH_LONG).show();
                registerViewModel.consumeError();
            }

            if (state.isSuccess()) {
                Toast.makeText(this, "Account created! Please sign in.", Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }
}
