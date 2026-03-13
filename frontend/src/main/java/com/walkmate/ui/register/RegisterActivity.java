package com.walkmate.ui.register;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.walkmate.frontend.R;
import com.walkmate.ui.login.LoginActivity;

public class RegisterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

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
    }

    private void initClickListeners() {
        AppCompatButton btnTabSignIn = findViewById(R.id.btn_tab_signin_reg);
        TextView tvFooterSignIn = findViewById(R.id.tv_footer_signin);

        View.OnClickListener goToLogin = v -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
            // Add a simple transition
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        };

        if (btnTabSignIn != null) btnTabSignIn.setOnClickListener(goToLogin);
        if (tvFooterSignIn != null) tvFooterSignIn.setOnClickListener(goToLogin);
    }
}