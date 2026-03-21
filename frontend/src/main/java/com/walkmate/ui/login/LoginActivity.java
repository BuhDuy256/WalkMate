package com.walkmate.ui.login;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Patterns;
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

import com.walkmate.frontend.R;
import com.walkmate.network.ApiClient;
import com.walkmate.network.AuthApiService;
import com.walkmate.ui.main.MainActivity;
import com.walkmate.ui.register.RegisterActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private static final String PREFS_AUTH = "walkmate_auth";
    private static final String KEY_ACCESS_TOKEN = "access_token";

    private EditText etEmail, etPassword;
    private ImageView ivTogglePassword;
    private AppCompatButton btnSignInAction;
    private boolean isPasswordVisible = false;
    private final AuthApiService authApiService = ApiClient.getAuthApiService();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.et_email_login);
        etPassword = findViewById(R.id.et_password_login);
        ivTogglePassword = findViewById(R.id.iv_toggle_password);

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

        initClickListeners();
        initLoginAction();
        setupPasswordToggle();
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

        if (btnTabSignUp != null) btnTabSignUp.setOnClickListener(goToRegister);
        if (tvFooterSignUp != null) tvFooterSignUp.setOnClickListener(goToRegister);
    }

    private void initLoginAction(){
        btnSignInAction = findViewById(R.id.btn_signin_action);
        if (btnSignInAction != null) {
            btnSignInAction.setOnClickListener(v -> {
                if (validateInput()) {
                    submitLogin();
                }
            });
        }
    }

    private void submitLogin() {
        setLoginLoading(true);

        LoginRequest request = new LoginRequest(
                etEmail.getText().toString().trim(),
                etPassword.getText().toString().trim()
        );

        authApiService.login(request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                setLoginLoading(false);

                LoginResponse body = response.body();
                if (response.isSuccessful() && body != null && !TextUtils.isEmpty(body.getAccessToken())) {
                    saveAccessToken(body.getAccessToken());
                    Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    return;
                }

                Toast.makeText(LoginActivity.this, "Invalid email or password", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable throwable) {
                setLoginLoading(false);
                Toast.makeText(LoginActivity.this, "Cannot connect to backend: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void saveAccessToken(String accessToken) {
        SharedPreferences prefs = getSharedPreferences(PREFS_AUTH, MODE_PRIVATE);
        prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).apply();
    }

    private void setLoginLoading(boolean isLoading) {
        if (btnSignInAction == null) {
            return;
        }

        btnSignInAction.setEnabled(!isLoading);
        btnSignInAction.setText(isLoading ? "Signing in..." : "Sign In ✦");
    }

    private boolean validateInput() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Please enter a valid email address");
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            return false;
        }
        return true;
    }
}