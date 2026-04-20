package com.walkmate.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
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
import com.walkmate.WalkMateApplication;
import com.walkmate.core.designsystem.view.WalkMateButton;
import com.walkmate.core.designsystem.view.WalkMateInputField;
import com.walkmate.core.event.AuthEvent;
import com.walkmate.core.event.AuthEventBus;
import com.walkmate.ui.auth.login.LoginViewModel;
import com.walkmate.ui.auth.login.LoginViewModelFactory;
import com.walkmate.ui.auth.login.LoginViewModelFactory;
import com.walkmate.ui.auth.register.RegisterActivity;
import com.walkmate.ui.main.MainActivity;

public class AuthActivity extends AppCompatActivity {

    private static final String TAG = "AuthActivity";

    private WalkMateInputField fieldEmail;
    private WalkMateInputField fieldPassword;
    private WalkMateButton btnSignIn;
    private MaterialButton btnGoogleSignIn;
    private MaterialButton btnGoogleSignIn;

    private LoginViewModel loginViewModel;
    private GoogleSignInClient googleSignInClient;

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::handleGoogleSignInResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WalkMateApplication app = (WalkMateApplication) getApplication();
        if (app.getSessionManager().hasUsableAccessToken()) {
            onLoginSuccess();
            return;
        }

        if (app.getSessionManager().getAccessToken() != null) {
            app.getSessionManager().clearSession();
        }

        setContentView(R.layout.activity_auth);

        LoginViewModelFactory factory = new LoginViewModelFactory(this);
        loginViewModel = new ViewModelProvider(this, factory).get(LoginViewModel.class);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        initViews();
        initClickListeners();
        observeUiState();
        observeAuthEventBus();
    }

    private void initViews() {
        fieldEmail            = findViewById(R.id.field_email);
        fieldPassword         = findViewById(R.id.field_password);
        btnSignIn             = findViewById(R.id.btn_signin_action);
        btnGoogleSignIn       = findViewById(R.id.btn_google_signin);
    }

    private void initClickListeners() {
        btnSignIn.setOnClickListener(v ->
                loginViewModel.login(fieldEmail.getText(), fieldPassword.getText()));

        btnGoogleSignIn.setOnClickListener(v -> {
            btnGoogleSignIn.setEnabled(false);
            googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
        });

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
            if (!state.isLoading()) {
                btnGoogleSignIn.setEnabled(true);
            }

            if (state.isForcedLogout()) {
                // Account suspended mid-login attempt — show message and stay on auth screen.
                Toast.makeText(this, state.getError(), Toast.LENGTH_LONG).show();
                loginViewModel.consumeError();
                return;
            }

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
     * Observes AuthEventBus for forced-logout events triggered by TokenRefreshAuthenticator.
     * Clears the session and relaunches this Activity with a clean back stack.
     */
    private void observeAuthEventBus() {
        AuthEventBus.getInstance().observe().observe(this, event -> {
            if (event == AuthEvent.FORCE_LOGOUT) {
                ((WalkMateApplication) getApplication()).getSessionManager().clearSession();
                AuthEventBus.getInstance().consumeEvent();
                Intent intent = new Intent(this, AuthActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        });
    }



    // ── Google Sign-In ────────────────────────────────────────────────────────

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

    // ── Navigation ────────────────────────────────────────────────────────────

    private void onLoginSuccess() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
