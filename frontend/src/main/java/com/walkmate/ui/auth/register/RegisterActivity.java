package com.walkmate.ui.auth.register;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.walkmate.R;
import com.walkmate.core.designsystem.view.WalkMateButton;
import com.walkmate.core.designsystem.view.WalkMateInputField;
import com.walkmate.ui.auth.login.LoginViewModel;
import com.walkmate.ui.auth.login.LoginViewModelFactory;
import com.walkmate.ui.main.MainActivity;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private WalkMateInputField fieldFullName;
    private WalkMateInputField fieldEmail;
    private WalkMateInputField fieldPassword;
    private WalkMateButton btnRegister;
    private MaterialButton btnGoogleSignIn;

    private RegisterViewModel registerViewModel;
    private LoginViewModel loginViewModel;
    private GoogleSignInClient googleSignInClient;

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::handleGoogleSignInResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        RegisterViewModelFactory registerFactory = new RegisterViewModelFactory(this);
        registerViewModel = new ViewModelProvider(this, registerFactory).get(RegisterViewModel.class);

        // Reuse LoginViewModel for the Google path — backend find-or-create handles new users
        LoginViewModelFactory loginFactory = new LoginViewModelFactory(this);
        loginViewModel = new ViewModelProvider(this, loginFactory).get(LoginViewModel.class);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        initViews();
        initClickListeners();
        observeRegisterState();
        observeLoginState();
    }

    private void initViews() {
        fieldFullName   = findViewById(R.id.field_fullname);
        fieldEmail      = findViewById(R.id.field_email);
        fieldPassword   = findViewById(R.id.field_password);
        btnRegister     = findViewById(R.id.btn_register_action);
        btnGoogleSignIn = findViewById(R.id.btn_google_signin);
    }

    private void initClickListeners() {
        btnRegister.setOnClickListener(v ->
                registerViewModel.register(
                        fieldFullName.getText(), fieldEmail.getText(), fieldPassword.getText()));

        btnGoogleSignIn.setOnClickListener(v -> {
            btnGoogleSignIn.setEnabled(false);
            googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
        });

        TextView tvSignInLink = findViewById(R.id.tv_signin_link);
        tvSignInLink.setOnClickListener(v -> finish());
    }

    private void observeRegisterState() {
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

    private void observeLoginState() {
        loginViewModel.getUiState().observe(this, state -> {
            if (state == null) return;

            if (!state.isLoading()) btnGoogleSignIn.setEnabled(true);

            if (state.getError() != null) {
                Toast.makeText(this, state.getError(), Toast.LENGTH_LONG).show();
                loginViewModel.consumeError();
            }

            if (state.isSuccess()) {
                // Google sign-in/register succeeded — go straight to home
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        });
    }

    private void handleGoogleSignInResult(ActivityResult result) {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            String idToken = account.getIdToken();
            if (idToken == null) {
                Log.w(TAG, "Google Sign-In succeeded but idToken is null");
                btnGoogleSignIn.setEnabled(true);
                Toast.makeText(this, "Google Sign-In failed. Please try again.", Toast.LENGTH_LONG).show();
                return;
            }
            loginViewModel.loginWithGoogle(idToken);
        } catch (ApiException e) {
            Log.w(TAG, "Google Sign-In failed, status code: " + e.getStatusCode());
            btnGoogleSignIn.setEnabled(true);
            if (e.getStatusCode() != 12501) {
                Toast.makeText(this, "Google Sign-In failed. Please try again.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
