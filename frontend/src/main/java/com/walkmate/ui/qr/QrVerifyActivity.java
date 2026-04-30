package com.walkmate.ui.qr;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.walkmate.R;
import com.walkmate.core.util.WindowInsetUtils;

public class QrVerifyActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID     = "session_id";
    public static final String EXTRA_PARTNER_NAME   = "partner_name";
    public static final String EXTRA_PARTNER_AVATAR = "partner_avatar";
    public static final String EXTRA_HOTSPOT_NAME   = "hotspot_name";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_verify);
        WindowInsetUtils.applyStatusBarPadding(findViewById(R.id.qrHeader));

        String sessionId    = getIntent().getStringExtra(EXTRA_SESSION_ID);
        String partnerName  = getIntent().getStringExtra(EXTRA_PARTNER_NAME);
        String partnerAvatar= getIntent().getStringExtra(EXTRA_PARTNER_AVATAR);
        String hotspotName  = getIntent().getStringExtra(EXTRA_HOTSPOT_NAME);

        if (sessionId == null) { finish(); return; }

        // Header
        TextView txtLocation  = findViewById(R.id.txtLocation);
        TextView txtSessionId = findViewById(R.id.txtSessionId);
        if (hotspotName  != null) txtLocation.setText(hotspotName);
        if (sessionId    != null) txtSessionId.setText(formatSessionId(sessionId));

        // Back
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // ViewPager2 + TabLayout
        ViewPager2 viewPager = findViewById(R.id.viewPager);
        TabLayout  tabLayout = findViewById(R.id.tabLayout);

        QrVerifyPagerAdapter adapter = new QrVerifyPagerAdapter(
                this, sessionId, partnerName, partnerAvatar, hotspotName);
        viewPager.setAdapter(adapter);
        // Prevent swipe from accidentally switching tabs while scanning
        viewPager.setUserInputEnabled(false);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            View customView = getLayoutInflater().inflate(R.layout.item_qr_tab, null);
            ImageView icon = customView.findViewById(R.id.tabIcon);
            TextView  text = customView.findViewById(R.id.tabText);
            if (position == 0) {
                icon.setImageResource(R.drawable.ic_qr_code);
                text.setText(R.string.qr_tab_my_code);
            } else {
                icon.setImageResource(R.drawable.ic_camera_small);
                text.setText(R.string.qr_tab_scan);
            }
            tab.setCustomView(customView);
        }).attach();

        // Colour the initially-selected tab (position 0)
        updateQrTabState(tabLayout.getTabAt(0), true);
        updateQrTabState(tabLayout.getTabAt(1), false);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab)   { updateQrTabState(tab, true);  }
            @Override public void onTabUnselected(TabLayout.Tab tab) { updateQrTabState(tab, false); }
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void updateQrTabState(TabLayout.Tab tab, boolean selected) {
        if (tab == null || tab.getCustomView() == null) return;
        int color = ContextCompat.getColor(this,
                selected ? R.color.orange_primary : R.color.text_muted);
        ImageView icon = tab.getCustomView().findViewById(R.id.tabIcon);
        TextView  text = tab.getCustomView().findViewById(R.id.tabText);
        icon.setColorFilter(color);
        text.setTextColor(color);
        text.setTypeface(null, selected
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private String formatSessionId(String rawUuid) {
        // Show last 4 chars of UUID as a short identifier, e.g. "WM-A1B2"
        String suffix = rawUuid.length() >= 4
                ? rawUuid.substring(rawUuid.length() - 4).toUpperCase()
                : rawUuid.toUpperCase();
        return "WM-" + suffix;
    }
}
