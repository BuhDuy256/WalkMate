package com.walkmate.ui.explore;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;

/**
 * Transparent full-screen View placed over the map container during SCANNING state.
 *
 * Draws 3 concentric orange rings that expand outward from the centre with
 * decreasing alpha — creating a repeating "ripple/pulse" effect.
 *
 * Animation is driven by a single ValueAnimator (0 → 1, infinite), which
 * advances each ring's phase offset by 1/RING_COUNT per cycle so they are
 * equally spaced in time.
 *
 * Touches are NOT intercepted; the view is fully transparent to hit-tests.
 */
public class PulseOverlayView extends View {

    // ── Constants ─────────────────────────────────────────────────────────
    private static final int   RING_COUNT       = 3;
    private static final long  CYCLE_DURATION   = 1_800L; // ms per full cycle
    private static final int   MAX_ALPHA        = 190;    // 0–255; peak opacity per ring
    private static final float MAX_RADIUS_RATIO = 0.38f;  // fraction of min(w, h)
    private static final int   PULSE_COLOR      = Color.parseColor("#FF7B3A"); // orange_primary

    // ── State ─────────────────────────────────────────────────────────────
    private final Paint   paint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Each element holds the current cycle phase [0.0, 1.0) for ring i. */
    private final float[] phases = new float[RING_COUNT];
    private       float   maxRadius;
    private       ValueAnimator animator;

    // ════════════════════════════════════════════════════════════════════
    // CONSTRUCTION
    // ════════════════════════════════════════════════════════════════════

    public PulseOverlayView(Context context) {
        super(context);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PULSE_COLOR);

        // Initialise phases evenly spaced so rings are staggered from the start.
        for (int i = 0; i < RING_COUNT; i++) {
            phases[i] = (float) i / RING_COUNT;
        }

        // Touches must fall through to the GoogleMap beneath.
        setClickable(false);
        setFocusable(false);
    }

    // ════════════════════════════════════════════════════════════════════
    // MEASURE / LAYOUT
    // ════════════════════════════════════════════════════════════════════

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        maxRadius = Math.min(w, h) * MAX_RADIUS_RATIO;
    }

    // ════════════════════════════════════════════════════════════════════
    // DRAW
    // ════════════════════════════════════════════════════════════════════

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (maxRadius <= 0f) return;

        float cx = getWidth()  / 2f;
        float cy = getHeight() / 2f;

        for (int i = 0; i < RING_COUNT; i++) {
            float phase  = phases[i];               // [0.0, 1.0)
            float radius = phase * maxRadius;
            int   alpha  = (int) ((1f - phase) * MAX_ALPHA);
            paint.setAlpha(alpha);
            canvas.drawCircle(cx, cy, radius, paint);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // ANIMATION CONTROL
    // ════════════════════════════════════════════════════════════════════

    /**
     * Starts (or restarts) the pulse animation.
     * Safe to call multiple times — cancels any running animation first.
     */
    public void startPulse() {
        stopPulse();

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(CYCLE_DURATION);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setInterpolator(new LinearInterpolator());

        animator.addUpdateListener(anim -> {
            float t = (float) anim.getAnimatedValue(); // 0.0 → 1.0
            for (int i = 0; i < RING_COUNT; i++) {
                // Advance each ring's phase by its fixed offset.
                phases[i] = (t + (float) i / RING_COUNT) % 1.0f;
            }
            invalidate();
        });

        animator.start();
    }

    /**
     * Stops the pulse animation and clears the canvas.
     * Safe to call when no animation is running.
     */
    public void stopPulse() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        // Clear all rings so a stale frame is not visible after stop.
        for (int i = 0; i < RING_COUNT; i++) {
            phases[i] = 0f;
        }
        invalidate();
    }
}
