package com.walkmate.ui.coordination;

import com.walkmate.R;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.RangeSlider;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * CoordinationActivity — Main screen for the WalkMate "Coordination Phase".
 *
 * Translates the React App.tsx state machine:
 *   "idle" → "hotspotSelected" → "createIntent" → "matching" → "matchResult"
 *
 * Each state shows/hides the appropriate UI layers with animations matching
 * the Framer Motion transitions from the original React code.
 */
public class CoordinationActivity extends AppCompatActivity {

    // ── State enum mirroring React's AppState ──────────────────────────
    private enum AppState {
        IDLE,
        HOTSPOT_SELECTED,
        CREATE_INTENT,
        MATCHING,
        MATCH_RESULT
    }

    // ── Data model mirroring React's Hotspot interface ─────────────────
    private static class Hotspot {
        final String id;
        final String name;
        final float xPercent; // 0‑100
        final float yPercent; // 0‑100

        Hotspot(String id, String name, float x, float y) {
            this.id = id;
            this.name = name;
            this.xPercent = x;
            this.yPercent = y;
        }
    }

    // ── Hotspot data matching MapView.tsx ───────────────────────────────
    private static final List<Hotspot> HOTSPOTS = Arrays.asList(
            new Hotspot("1", "Công viên Tao Đàn", 38f, 42f),
            new Hotspot("2", "Hồ Con Rùa", 62f, 30f),
            new Hotspot("3", "Công viên Gia Định", 55f, 65f),
            new Hotspot("4", "Công viên Lê Văn Tám", 25f, 58f)
    );

    // ── Current state & selection ──────────────────────────────────────
    private AppState currentState = AppState.IDLE;
    private Hotspot selectedHotspot = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable matchingTimerRunnable;

    // ── Views ──────────────────────────────────────────────────────────
    private FrameLayout mapContainer;
    private View dimOverlay;
    private LinearLayout hotspotCtaCard;
    private TextView txtHotspotName;
    private MaterialButton btnSetIntent;
    private View btnCloseSheet;
    private ScrollView intentSheet;
    private FrameLayout matchingOverlay;
    private TextView txtScanning;
    private View pulseRingOuter;
    private View pulseRingInner;
    private FrameLayout matchResultContainer;
    private ImageView imgMatchAvatar;
    private MaterialButton btnPass;
    private MaterialButton btnAccept;
    private MaterialButton btnFindMatch;

    // Sliders
    private RangeSlider sliderTime;
    private RangeSlider sliderAge;
    private TextView txtTimeStart, txtTimeEnd;
    private TextView txtAgeMin, txtAgeMax;

    // Marker views (to update selected state)
    private View[] markerViews;

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
        setupSliders();
        transitionTo(AppState.IDLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (matchingTimerRunnable != null) {
            handler.removeCallbacks(matchingTimerRunnable);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // VIEW BINDING (translates React's JSX → findViewById)
    // ════════════════════════════════════════════════════════════════════

    private void bindViews() {
        mapContainer = findViewById(R.id.mapContainer);
        dimOverlay = findViewById(R.id.dimOverlay);
        hotspotCtaCard = findViewById(R.id.hotspotCtaCard);
        txtHotspotName = findViewById(R.id.txtHotspotName);
        btnSetIntent = findViewById(R.id.btnSetIntent);
        btnCloseSheet = findViewById(R.id.btnCloseSheet);
        intentSheet = findViewById(R.id.intentSheet);
        matchingOverlay = findViewById(R.id.matchingOverlay);
        txtScanning = findViewById(R.id.txtScanning);
        pulseRingOuter = findViewById(R.id.pulseRingOuter);
        pulseRingInner = findViewById(R.id.pulseRingInner);
        matchResultContainer = findViewById(R.id.matchResultContainer);
        imgMatchAvatar = findViewById(R.id.imgMatchAvatar);
        btnPass = findViewById(R.id.btnPass);
        btnAccept = findViewById(R.id.btnAccept);
        btnFindMatch = findViewById(R.id.btnFindMatch);

        sliderTime = findViewById(R.id.sliderTime);
        sliderAge = findViewById(R.id.sliderAge);
        txtTimeStart = findViewById(R.id.txtTimeStart);
        txtTimeEnd = findViewById(R.id.txtTimeEnd);
        txtAgeMin = findViewById(R.id.txtAgeMin);
        txtAgeMax = findViewById(R.id.txtAgeMax);
    }

    // ════════════════════════════════════════════════════════════════════
    // MAP SETUP (translates MapView.tsx hotspot markers)
    // ════════════════════════════════════════════════════════════════════

    private void setupMap() {
        markerViews = new View[HOTSPOTS.size()];

        // Wait for layout pass to get actual container dimensions
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

                // Position: translate(-50%, -100%) equivalent
                markerView.measure(
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                );
                int markerW = markerView.getMeasuredWidth();
                int markerH = markerView.getMeasuredHeight();

                float pixelX = (hotspot.xPercent / 100f) * containerW;
                float pixelY = (hotspot.yPercent / 100f) * containerH;

                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                markerView.setLayoutParams(lp);
                markerView.setX(pixelX - markerW / 2f);
                markerView.setY(pixelY - markerH);

                markerView.setOnClickListener(v -> onHotspotClicked(hotspot, index));

                mapContainer.addView(markerView);
                markerViews[i] = markerView;
            }
        });
    }

    /**
     * Updates marker visuals: selected marker gets the orange gradient background
     * and white text; others revert to white bg with dark text.
     * Mirrors the isSelected ternary in MapView.tsx.
     */
    private void updateMarkerSelection(int selectedIndex) {
        for (int i = 0; i < markerViews.length; i++) {
            if (markerViews[i] == null) continue;

            View pill = markerViews[i].findViewById(R.id.markerPill);
            TextView label = markerViews[i].findViewById(R.id.markerLabel);
            View tail = markerViews[i].findViewById(R.id.markerTail);

            boolean isSelected = (i == selectedIndex);

            // Background
            pill.setBackgroundResource(isSelected
                    ? R.drawable.bg_marker_selected
                    : R.drawable.bg_marker_unselected);

            // Text color
            label.setTextColor(isSelected
                    ? Color.WHITE
                    : Color.parseColor("#332218"));

            // Tail color
            GradientDrawable tailBg = new GradientDrawable();
            tailBg.setShape(GradientDrawable.RECTANGLE);
            tailBg.setColor(isSelected
                    ? Color.parseColor("#FF7B3A")
                    : Color.WHITE);
            tail.setBackground(tailBg);

            // Scale animation: selected = 1.25, others = 1.0
            float targetScale = isSelected ? 1.25f : 1.0f;
            markerViews[i].animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .setDuration(300)
                    .start();

            // Z-order
            markerViews[i].setTranslationZ(isSelected ? 20f : 10f);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // CLICK LISTENERS (translates React onClick handlers)
    // ════════════════════════════════════════════════════════════════════

    private void setupListeners() {
        // "Set Walking Intent" button in the CTA card → open sheet
        btnSetIntent.setOnClickListener(v -> {
            if (selectedHotspot != null) {
                transitionTo(AppState.CREATE_INTENT);
            }
        });

        // Close button on the intent sheet → back to hotspot selected
        btnCloseSheet.setOnClickListener(v -> transitionTo(AppState.HOTSPOT_SELECTED));

        // Dim overlay tap → dismiss current overlay/sheet
        dimOverlay.setClickable(true);
        dimOverlay.setFocusable(true);
        dimOverlay.setOnClickListener(v -> {
            if (currentState == AppState.CREATE_INTENT) {
                transitionTo(AppState.HOTSPOT_SELECTED);
            } else if (currentState == AppState.MATCH_RESULT) {
                selectedHotspot = null;
                transitionTo(AppState.IDLE);
            }
        });

        // "Find Match" button in the bottom sheet → start matching
        btnFindMatch.setOnClickListener(v -> transitionTo(AppState.MATCHING));

        // Match result actions
        btnAccept.setOnClickListener(v -> {
            selectedHotspot = null;
            transitionTo(AppState.IDLE);
        });

        btnPass.setOnClickListener(v -> {
            selectedHotspot = null;
            transitionTo(AppState.IDLE);
        });
    }

    /** Hotspot marker tap → select and show CTA. */
    private void onHotspotClicked(Hotspot hotspot, int index) {
        selectedHotspot = hotspot;
        updateMarkerSelection(index);
        transitionTo(AppState.HOTSPOT_SELECTED);
    }

    // ════════════════════════════════════════════════════════════════════
    // SLIDER SETUP (translates React RangeSlider + formatTime)
    // ════════════════════════════════════════════════════════════════════

    private void setupSliders() {
        // Time slider: default [16, 22], range 6–24, step 0.5
        sliderTime.setValues(16f, 22f);
        sliderTime.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> values = slider.getValues();
            txtTimeStart.setText(formatTime(values.get(0)));
            txtTimeEnd.setText(formatTime(values.get(1)));
        });

        // Age slider: default [18, 40], range 16–65, step 1
        sliderAge.setValues(18f, 40f);
        sliderAge.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> values = slider.getValues();
            txtAgeMin.setText(String.valueOf(Math.round(values.get(0))));
            txtAgeMax.setText(String.valueOf(Math.round(values.get(1))));
        });
    }

    /**
     * Mirrors the React formatTime function:
     *   const h = Math.floor(val);
     *   const m = (val % 1) * 60;
     */
    private String formatTime(float val) {
        int h = (int) Math.floor(val);
        int m = (int) ((val % 1) * 60);
        return String.format(Locale.getDefault(), "%02d:%02d", h, m);
    }

    // ════════════════════════════════════════════════════════════════════
    // STATE MACHINE (translates React useState<AppState> + useEffect)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Core state transition method. Mirrors the React pattern where
     * setState() triggers re-render and useEffect watches state changes.
     */
    private void transitionTo(AppState newState) {
        AppState oldState = currentState;
        currentState = newState;

        // Cancel any pending matching timer
        if (matchingTimerRunnable != null) {
            handler.removeCallbacks(matchingTimerRunnable);
            matchingTimerRunnable = null;
        }

        // Stop pulse animations if leaving MATCHING
        if (oldState == AppState.MATCHING && newState != AppState.MATCHING) {
            stopPulseAnimation();
        }

        // Determine dim state: dimmed when createIntent or matchResult
        boolean dimmed = (newState == AppState.CREATE_INTENT || newState == AppState.MATCH_RESULT);
        animateDimOverlay(dimmed);

        switch (newState) {
            case IDLE:
                hideWithAnim(hotspotCtaCard, R.anim.fade_out);
                hideWithAnim(intentSheet, R.anim.slide_down);
                hideView(matchingOverlay);
                hideWithAnim(matchResultContainer, R.anim.fade_out);
                // Deselect all markers
                updateMarkerSelection(-1);
                break;

            case HOTSPOT_SELECTED:
                // Show CTA card, hide other overlays
                hideWithAnim(intentSheet, R.anim.slide_down);
                hideView(matchingOverlay);
                hideWithAnim(matchResultContainer, R.anim.fade_out);
                // Update CTA card content
                if (selectedHotspot != null) {
                    txtHotspotName.setText(selectedHotspot.name);
                }
                showWithAnim(hotspotCtaCard, R.anim.slide_up);
                break;

            case CREATE_INTENT:
                hideWithAnim(hotspotCtaCard, R.anim.fade_out);
                hideView(matchingOverlay);
                hideView(matchResultContainer);
                showWithAnim(intentSheet, R.anim.slide_up);
                break;

            case MATCHING:
                hideWithAnim(intentSheet, R.anim.slide_down);
                hideView(hotspotCtaCard);
                hideView(matchResultContainer);
                // Update scanning text
                if (selectedHotspot != null) {
                    txtScanning.setText(
                            String.format(getString(R.string.scanning_format), selectedHotspot.name));
                }
                showWithAnim(matchingOverlay, R.anim.scale_in);
                startPulseAnimation();

                // Auto-advance after 3s (mirrors React useEffect timer)
                matchingTimerRunnable = () -> transitionTo(AppState.MATCH_RESULT);
                handler.postDelayed(matchingTimerRunnable, 3000);
                break;

            case MATCH_RESULT:
                hideView(matchingOverlay);
                hideView(hotspotCtaCard);
                hideView(intentSheet);
                // Load avatar placeholder (in production, use Glide/Coil)
                imgMatchAvatar.setImageResource(R.drawable.bg_warm_circle); // placeholder
                showWithAnim(matchResultContainer, R.anim.scale_in);
                break;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // ANIMATION HELPERS
    // ════════════════════════════════════════════════════════════════════

    /** Show a view with an enter animation. */
    private void showWithAnim(View view, int animResId) {
        if (view.getVisibility() == View.VISIBLE) return;
        view.setVisibility(View.VISIBLE);
        Animation anim = AnimationUtils.loadAnimation(this, animResId);
        view.startAnimation(anim);
    }

    /** Hide a view with an exit animation. */
    private void hideWithAnim(View view, int animResId) {
        if (view.getVisibility() != View.VISIBLE) return;
        Animation anim = AnimationUtils.loadAnimation(this, animResId);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation a) {}
            @Override public void onAnimationRepeat(Animation a) {}
            @Override
            public void onAnimationEnd(Animation a) {
                view.setVisibility(View.GONE);
            }
        });
        view.startAnimation(anim);
    }

    /** Immediately hide without animation. */
    private void hideView(View view) {
        view.clearAnimation();
        view.setVisibility(View.GONE);
    }

    /** Animate the dim overlay in/out. */
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

    // ── Pulse animation (mirrors Framer Motion animate on MatchingOverlay) ──

    private AnimatorSet pulseAnimatorSet;

    /**
     * Recreates the dual-ring pulse from MatchingOverlay.tsx:
     *   outer: scale [1→1.6→1], opacity [0.6→0→0.6], 1.5s infinite
     *   inner: scale [1→1.3→1], opacity [0.8→0.2→0.8], 1.5s infinite, delay 0.3s
     */
    private void startPulseAnimation() {
        // Outer ring
        ObjectAnimator outerScaleX = ObjectAnimator.ofFloat(pulseRingOuter, "scaleX", 1f, 1.6f, 1f);
        ObjectAnimator outerScaleY = ObjectAnimator.ofFloat(pulseRingOuter, "scaleY", 1f, 1.6f, 1f);
        ObjectAnimator outerAlpha = ObjectAnimator.ofFloat(pulseRingOuter, "alpha", 0.6f, 0f, 0.6f);
        outerScaleX.setRepeatCount(ObjectAnimator.INFINITE);
        outerScaleY.setRepeatCount(ObjectAnimator.INFINITE);
        outerAlpha.setRepeatCount(ObjectAnimator.INFINITE);
        outerScaleX.setDuration(1500);
        outerScaleY.setDuration(1500);
        outerAlpha.setDuration(1500);

        // Inner ring
        ObjectAnimator innerScaleX = ObjectAnimator.ofFloat(pulseRingInner, "scaleX", 1f, 1.3f, 1f);
        ObjectAnimator innerScaleY = ObjectAnimator.ofFloat(pulseRingInner, "scaleY", 1f, 1.3f, 1f);
        ObjectAnimator innerAlpha = ObjectAnimator.ofFloat(pulseRingInner, "alpha", 0.8f, 0.2f, 0.8f);
        innerScaleX.setRepeatCount(ObjectAnimator.INFINITE);
        innerScaleY.setRepeatCount(ObjectAnimator.INFINITE);
        innerAlpha.setRepeatCount(ObjectAnimator.INFINITE);
        innerScaleX.setDuration(1500);
        innerScaleY.setDuration(1500);
        innerAlpha.setDuration(1500);
        innerScaleX.setStartDelay(300);
        innerScaleY.setStartDelay(300);
        innerAlpha.setStartDelay(300);

        pulseAnimatorSet = new AnimatorSet();
        pulseAnimatorSet.playTogether(
                outerScaleX, outerScaleY, outerAlpha,
                innerScaleX, innerScaleY, innerAlpha
        );
        pulseAnimatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimatorSet.start();
    }

    private void stopPulseAnimation() {
        if (pulseAnimatorSet != null) {
            pulseAnimatorSet.cancel();
            pulseAnimatorSet = null;
        }
        // Reset to default
        pulseRingOuter.setScaleX(1f);
        pulseRingOuter.setScaleY(1f);
        pulseRingOuter.setAlpha(0.6f);
        pulseRingInner.setScaleX(1f);
        pulseRingInner.setScaleY(1f);
        pulseRingInner.setAlpha(0.8f);
    }

    // ════════════════════════════════════════════════════════════════════
    // BACK PRESS HANDLING
    // ════════════════════════════════════════════════════════════════════

    @Override
    public void onBackPressed() {
        switch (currentState) {
            case MATCH_RESULT:
                // Back from result → idle
                selectedHotspot = null;
                transitionTo(AppState.IDLE);
                break;
            case MATCHING:
                // Can't back out of matching (auto-advances)
                break;
            case CREATE_INTENT:
                // Back from sheet → hotspotSelected (mirrors onClose)
                transitionTo(AppState.HOTSPOT_SELECTED);
                break;
            case HOTSPOT_SELECTED:
                // Back from CTA → idle
                selectedHotspot = null;
                transitionTo(AppState.IDLE);
                break;
            default:
                super.onBackPressed();
                break;
        }
    }
}
