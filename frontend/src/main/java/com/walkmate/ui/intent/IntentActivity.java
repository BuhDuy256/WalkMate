package com.walkmate.ui.intent;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.walkmate.R;

public class IntentActivity extends AppCompatActivity {

    public static final int TAB_CREATE = 0;
    public static final int TAB_MATCHING = 1;
    public static final int TAB_RESULT = 2;

    private ViewPager2 viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intent);

        viewPager = findViewById(R.id.vp_intent);
        viewPager.setAdapter(new IntentPagerAdapter(this));
        viewPager.setOffscreenPageLimit(2);

        // Keep transitions feeling like panel slides instead of full-screen fades.
        viewPager.setPageTransformer((page, position) -> {
            float absPos = Math.abs(position);
            page.setAlpha(1f - Math.min(0.12f, absPos * 0.12f));
            page.setTranslationX(-position * 20f);
        });
    }

    public void openCreateTab() {
        if (viewPager != null) {
            viewPager.setCurrentItem(TAB_CREATE, true);
        }
    }

    public void openMatchingTab() {
        if (viewPager != null) {
            viewPager.setCurrentItem(TAB_MATCHING, true);
        }
    }

    public void openResultTab() {
        if (viewPager != null) {
            viewPager.setCurrentItem(TAB_RESULT, true);
        }
    }
}
