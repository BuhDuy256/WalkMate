package com.walkmate.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.walkmate.R;

public class AuthActivity extends AppCompatActivity {

    public static final String EXTRA_START_TAB = "extra_start_tab";
    public static final int TAB_LOGIN = 0;
    public static final int TAB_REGISTER = 1;

    private ViewPager2 viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        viewPager = findViewById(R.id.vp_auth_forms);

        AuthPagerAdapter pagerAdapter = new AuthPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(1);

        // Keep horizontal slide as primary transition and avoid fade-like effect.
        viewPager.setPageTransformer((@NonNull android.view.View page, float position) -> {
            float absPosition = Math.abs(position);
            page.setAlpha(1f - Math.min(0.08f, absPosition * 0.08f));
            page.setTranslationX(-position * 12f);
        });

        int startTab = getIntent().getIntExtra(EXTRA_START_TAB, TAB_LOGIN);
        if (startTab == TAB_REGISTER) {
            viewPager.setCurrentItem(TAB_REGISTER, false);
        }
    }

    public void showLoginTab() {
        if (viewPager != null) {
            viewPager.setCurrentItem(TAB_LOGIN, true);
        }
    }

    public void showRegisterTab() {
        if (viewPager != null) {
            viewPager.setCurrentItem(TAB_REGISTER, true);
        }
    }

    public void onLoginSuccess() {
        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    public void onRegisterSuccess() {
        Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show();
        showLoginTab();
    }
}
