package com.walkmate.ui.register;

import android.content.Intent;
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
import com.walkmate.network.ApiResponse;
import com.walkmate.ui.login.LoginActivity;

import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {
    private EditText etFullName;
    private EditText etEmail;
    private EditText etPassword;
    private ImageView ivTogglePassword;
    private AppCompatButton btnRegisterAction;
    private boolean isPasswordVisible = false;
    private final AuthApiService authApiService = ApiClient.getAuthApiService();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

        etFullName = findViewById(R.id.et_fullname);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        ivTogglePassword = findViewById(R.id.iv_toggle_password_reg);

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

        initClickListeners();
        initRegisterAction();
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
        AppCompatButton btnTabSignIn = findViewById(R.id.btn_tab_signin_reg);
        TextView tvFooterSignIn = findViewById(R.id.tv_footer_signin);

        View.OnClickListener goToLogin = v -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        };

        if (btnTabSignIn != null) btnTabSignIn.setOnClickListener(goToLogin);
        if (tvFooterSignIn != null) tvFooterSignIn.setOnClickListener(goToLogin);
    }

    private void initRegisterAction(){
        btnRegisterAction = findViewById(R.id.btn_register_action);
        if (btnRegisterAction != null) {
            btnRegisterAction.setOnClickListener(v -> {
                if (validateInput()) {
                    submitRegister();
                }
            });
        }
    }

    private void submitRegister() {
        setRegisterLoading(true);

        RegisterRequest request = new RegisterRequest(
                etFullName.getText().toString().trim(),
                etEmail.getText().toString().trim(),
                etPassword.getText().toString().trim()
        );

        authApiService.register(request).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                setRegisterLoading(false);

                ApiResponse body = response.body();
                if (response.isSuccessful() && body != null && "success".equalsIgnoreCase(body.getCode())) {
                    Toast.makeText(RegisterActivity.this, body.getMessage(), Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                    startActivity(intent);
                    finish();
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    return;
                }

                String message = body != null && body.getMessage() != null
                        ? body.getMessage()
                        : response.code() == 409 ? "Email already exists" : "Registration failed with status code: " + response.code();
                Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_LONG).show();
                
            }

            @Override
            public void onFailure(Call<ApiResponse> call, Throwable throwable) {
                setRegisterLoading(false);
                Toast.makeText(RegisterActivity.this, "Cannot connect to backend: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setRegisterLoading(boolean isLoading) {
        if (btnRegisterAction == null) {
            return;
        }

        btnRegisterAction.setEnabled(!isLoading);
        btnRegisterAction.setText(isLoading ? "Creating..." : "Create Account ✦");
    }

    private boolean validateInput() {
        String fullName = etFullName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(fullName)) {
            etFullName.setError("Full Name is required");
            return false;
        }
        if (fullName.length() < 5) {
            etFullName.setError("Name must be at least 5 characters");
            return false;
        }
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
        if (!isValidPassword(password)) {
            etPassword.setError("Password must be at least 8 characters, include uppercase, number, and special character");
            return false;
        }
        return true;
    }

    private boolean isValidPassword(String password) {
        String passwordPattern = "^(?=.*[0-9])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$";
        return Pattern.compile(passwordPattern).matcher(password).matches();
    }
}