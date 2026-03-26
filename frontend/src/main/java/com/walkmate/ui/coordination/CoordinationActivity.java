package com.walkmate.ui.coordination;

import com.walkmate.R;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import java.util.Arrays;
import java.util.List;

/**
 * CoordinationActivity — Main screen for the WalkMate "Coordination Phase".
 *
 * Now acts strictly as the base map host (Layers 1-3 only).
 * Layers 4-6 are handled by separate Fragments:
 * - CreateIntentBottomSheetFragment (Phase 1: intent form)
 * - MatchingOverlayFragment (Phase 2: scanning pulse)
 * - MatchResultFragment (Phase 3: match result dialog)
 */
public class CoordinationActivity extends AppCompatActivity
        implements CreateIntentBottomSheetFragment.OnIntentActionListener,
        MatchingOverlayFragment.OnMatchFoundListener,
        MatchResultFragment.OnMatchResultActionListener {

    // ── State enum ──────────────────────────────────────────────────────
    private enum AppState {
        IDLE,
        HOTSPOT_SELECTED,
        CREATE_INTENT,
        MATCHING,
        MATCH_RESULT
    }

    // ── Data model ──────────────────────────────────────────────────────
    private static class Hotspot {
        final String id;
        final String name;
        final float xPercent;
        final float yPercent;

        Hotspot(String id, String name, float x, float y) {
            this.id = id;
            this.name = name;
            this.xPercent = x;
            this.yPercent = y;
        }
    }

    private static final List<Hotspot> HOTSPOTS = Arrays.asList(
            new Hotspot("1", "Công viên Tao Đàn", 38f, 42f),
            new Hotspot("2", "Hồ Con Rùa", 62f, 30f),
            new Hotspot("3", "Công viên Gia Định", 55f, 65f),
            new Hotspot("4", "Công viên Lê Văn Tám", 25f, 58f));

    // ── Current state & selection ────────────────────────────────────────
    private AppState currentState = AppState.IDLE;
    private Hotspot selectedHotspot = null;

    // ── Views (only Layers 1-3 now) ─────────────────────────────────────
    private FrameLayout mapContainer;
    private View dimOverlay;
    private LinearLayout hotspotCtaCard;
    private TextView txtHotspotName;
    private MaterialButton btnSetIntent;

    // Marker views
    private View[] markerViews;

    // Fragment tags
    private static final String TAG_INTENT_SHEET = "create_intent";
    private static final String TAG_MATCHING = "matching_overlay";
    private static final String TAG_MATCH_RESULT = "match_result";

    // ════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_coordination);

        bindViews();
        setupMap();
        setupListeners();
        setupBackPressHandling();
        transitionTo(AppState.IDLE);
    }

    // ════════════════════════════════════════════════════════════════════
    // VIEW BINDING (only Layers 1-3)
    // ════════════════════════════════════════════════════════════════════

    private void bindViews() {
        mapContainer = findViewById(R.id.mapContainer);
        dimOverlay = findViewById(R.id.dimOverlay);
        hotspotCtaCard = findViewById(R.id.hotspotCtaCard);
        txtHotspotName = findViewById(R.id.txtHotspotName);
        btnSetIntent = findViewById(R.id.btnSetIntent);
    }

    // ════════════════════════════════════════════════════════════════════
    // MAP SETUP
    // ════════════════════════════════════════════════════════════════════

    private void setupMap() {
        markerViews = new View[HOTSPOTS.size()];

        mapContainer.post(() -> {
            int containerW = mapContainer.getWidth();
            int containerH = mapContainer.getHeight();

            for (int i = 0; i < HOTSPOTS.size(); i++) {
                final Hotspot hotspot = HOTSPOTS.get(i);
                final int index = i;

                View markerView = LayoutInflater.from(this)
                        .inflate(R.layout.item_map_marker, mapContainer, false);

                TextView label = markerView.findViewById(R.id.markerLabel);
                label.setText(hotspot.name);

                markerView.measure(
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                int markerW = markerView.getMeasuredWidth();
                int markerH = markerView.getMeasuredHeight();

                float pixelX = (hotspot.xPercent / 100f) * containerW;
                float pixelY = (hotspot.yPercent / 100f) * containerH;

                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                markerView.setLayoutParams(lp);
                markerView.setX(pixelX - markerW / 2f);
                markerView.setY(pixelY - markerH);

                markerView.setOnClickListener(v -> onHotspotClicked(hotspot, index));

                mapContainer.addView(markerView);
                markerViews[i] = markerView;
            }
        });
    }

    private void updateMarkerSelection(int selectedIndex) {
        for (int i = 0; i < markerViews.length; i++) {
            if (markerViews[i] == null)
                continue;

            View pill = markerViews[i].findViewById(R.id.markerPill);
            TextView label = markerViews[i].findViewById(R.id.markerLabel);
            View tail = markerViews[i].findViewById(R.id.markerTail);

            boolean isSelected = (i == selectedIndex);

            pill.setBackgroundResource(isSelected
                    ? R.drawable.bg_marker_selected
                    : R.drawable.bg_marker_unselected);

            label.setTextColor(isSelected
                    ? Color.WHITE
                    : Color.parseColor("#332218"));

            GradientDrawable tailBg = new GradientDrawable();
            tailBg.setShape(GradientDrawable.RECTANGLE);
            tailBg.setColor(isSelected
                    ? Color.parseColor("#FF7B3A")
                    : Color.WHITE);
            tail.setBackground(tailBg);

            float targetScale = isSelected ? 1.25f : 1.0f;
            markerViews[i].animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .setDuration(300)
                    .start();

            markerViews[i].setTranslationZ(isSelected ? 20f : 10f);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // CLICK LISTENERS
    // ════════════════════════════════════════════════════════════════════

    private void setupListeners() {
        btnSetIntent.setOnClickListener(v -> {
            if (selectedHotspot != null) {
                transitionTo(AppState.CREATE_INTENT);
            }
        });

        dimOverlay.setClickable(true);
        dimOverlay.setFocusable(true);
        dimOverlay.setOnClickListener(v -> {
            if (currentState == AppState.MATCH_RESULT) {
                selectedHotspot = null;
                transitionTo(AppState.IDLE);
            }
        });
    }

    private void onHotspotClicked(Hotspot hotspot, int index) {
        selectedHotspot = hotspot;
        updateMarkerSelection(index);
        if (currentState != AppState.CREATE_INTENT) {
            transitionTo(AppState.HOTSPOT_SELECTED);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // FRAGMENT CALLBACK IMPLEMENTATIONS
    // ════════════════════════════════════════════════════════════════════

    @Override
    public void onFindMatchClicked() {
        transitionTo(AppState.MATCHING);
    }

    @Override
    public void onSheetDismissed() {
        if (currentState == AppState.CREATE_INTENT) {
            currentState = AppState.HOTSPOT_SELECTED;
            animateDimOverlay(false);
            showWithAnim(hotspotCtaCard, R.anim.slide_up);
        }
    }

    @Override
    public void onMatchTimerComplete() {
        dismissFragmentByTag(TAG_MATCHING);
        transitionTo(AppState.MATCH_RESULT);
    }

    @Override
    public void onAcceptClicked() {
        selectedHotspot = null;
        transitionTo(AppState.IDLE);
    }

    @Override
    public void onPassClicked() {
        selectedHotspot = null;
        transitionTo(AppState.IDLE);
    }

    // ════════════════════════════════════════════════════════════════════
    // STATE MACHINE
    // ════════════════════════════════════════════════════════════════════

    private void transitionTo(AppState newState) {
        currentState = newState;

        switch (newState) {
            case IDLE:
                hideWithAnim(hotspotCtaCard, R.anim.fade_out);
                animateDimOverlay(false);
                dismissFragmentByTag(TAG_INTENT_SHEET);
                dismissFragmentByTag(TAG_MATCHING);
                dismissFragmentByTag(TAG_MATCH_RESULT);
                updateMarkerSelection(-1);
                break;

            case HOTSPOT_SELECTED:
                animateDimOverlay(false);
                dismissFragmentByTag(TAG_INTENT_SHEET);
                dismissFragmentByTag(TAG_MATCHING);
                dismissFragmentByTag(TAG_MATCH_RESULT);
                if (selectedHotspot != null) {
                    txtHotspotName.setText(selectedHotspot.name);
                }
                showWithAnim(hotspotCtaCard, R.anim.slide_up);
                break;

            case CREATE_INTENT:
                hideWithAnim(hotspotCtaCard, R.anim.fade_out);
                animateDimOverlay(true);
                showCreateIntentSheet();
                break;

            case MATCHING:
                hideView(hotspotCtaCard);
                animateDimOverlay(true);
                dismissFragmentByTag(TAG_INTENT_SHEET);
                showMatchingOverlay();
                break;

            case MATCH_RESULT:
                hideView(hotspotCtaCard);
                animateDimOverlay(true);
                dismissFragmentByTag(TAG_MATCHING);
                showMatchResult();
                break;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // FRAGMENT MANAGEMENT
    // ════════════════════════════════════════════════════════════════════

    private void showCreateIntentSheet() {
        if (getSupportFragmentManager().findFragmentByTag(TAG_INTENT_SHEET) != null)
            return;

        CreateIntentBottomSheetFragment fragment = new CreateIntentBottomSheetFragment();
        fragment.setOnIntentActionListener(this);
        fragment.show(getSupportFragmentManager(), TAG_INTENT_SHEET);
    }

    private void showMatchingOverlay() {
        if (getSupportFragmentManager().findFragmentByTag(TAG_MATCHING) != null)
            return;

        String name = selectedHotspot != null ? selectedHotspot.name : "";
        MatchingOverlayFragment fragment = MatchingOverlayFragment.newInstance(name);
        fragment.setOnMatchFoundListener(this);
        fragment.show(getSupportFragmentManager(), TAG_MATCHING);
    }

    private void showMatchResult() {
        if (getSupportFragmentManager().findFragmentByTag(TAG_MATCH_RESULT) != null)
            return;

        MatchResultFragment fragment = new MatchResultFragment();
        fragment.setOnMatchResultActionListener(this);
        fragment.show(getSupportFragmentManager(), TAG_MATCH_RESULT);
    }

    private void dismissFragmentByTag(String tag) {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if (fragment instanceof androidx.fragment.app.DialogFragment) {
            ((androidx.fragment.app.DialogFragment) fragment).dismissAllowingStateLoss();
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // ANIMATION HELPERS (only for Layers 1-3 views)
    // ════════════════════════════════════════════════════════════════════

    private void showWithAnim(View view, int animResId) {
        if (view.getVisibility() == View.VISIBLE)
            return;
        view.setVisibility(View.VISIBLE);
        Animation anim = AnimationUtils.loadAnimation(this, animResId);
        view.startAnimation(anim);
    }

    private void hideWithAnim(View view, int animResId) {
        if (view.getVisibility() != View.VISIBLE)
            return;
        Animation anim = AnimationUtils.loadAnimation(this, animResId);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation a) {
            }

            @Override
            public void onAnimationRepeat(Animation a) {
            }

            @Override
            public void onAnimationEnd(Animation a) {
                view.setVisibility(View.GONE);
            }
        });
        view.startAnimation(anim);
    }

    private void hideView(View view) {
        view.clearAnimation();
        view.setVisibility(View.GONE);
    }

    private void animateDimOverlay(boolean show) {
        if (show) {
            dimOverlay.setVisibility(View.VISIBLE);
            dimOverlay.setAlpha(0f);
            dimOverlay.animate().alpha(1f).setDuration(300).start();
        } else {
            dimOverlay.animate().alpha(0f).setDuration(250)
                    .withEndAction(() -> dimOverlay.setVisibility(View.GONE))
                    .start();
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // BACK PRESS HANDLING
    // ════════════════════════════════════════════════════════════════════

    private void setupBackPressHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!handleBackPressedForState()) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    private boolean handleBackPressedForState() {
        switch (currentState) {
            case MATCH_RESULT:
            case HOTSPOT_SELECTED:
                selectedHotspot = null;
                transitionTo(AppState.IDLE);
                return true;
            case MATCHING:
                // Can't back out of matching (auto-advances)
                return true;
            case CREATE_INTENT:
                transitionTo(AppState.HOTSPOT_SELECTED);
                return true;
            default:
                return false;
        }
    }
}
