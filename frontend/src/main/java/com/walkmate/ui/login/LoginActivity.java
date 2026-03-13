package com.walkmate.ui.login;

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
import com.walkmate.ui.register.RegisterActivity;

import java.util.regex.Pattern;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private ImageView ivTogglePassword;
    private boolean isPasswordVisible = false;

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
        AppCompatButton btnSignInAction = findViewById(R.id.btn_signin_action);
        if (btnSignInAction != null) {
            btnSignInAction.setOnClickListener(v -> {
                if (validateInput()) {
                    Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();
                }
            });
        }
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