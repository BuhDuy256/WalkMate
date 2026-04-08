package com.walkmate.core.designsystem.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.CountDownTimer;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import com.walkmate.R;

import java.time.Instant;

/**
 * CountdownTimerView — a self-contained countdown text view.
 *
 * <p>Accepts either an ISO-8601 expiry timestamp or a pre-parsed epoch-ms value,
 * then ticks down every second formatting the remaining time as {@code MM:SS}.
 * When the remaining time drops below the configurable urgent threshold the text
 * colour switches to {@code wm_urgentColor}; on expiry the text reads "Expired"
 * and the optional {@link OnExpiredListener} fires.</p>
 *
 * <h3>Recycler-safe usage</h3>
 * Call {@link #cancelCountdown()} from {@code onViewRecycled()} in your adapter.
 * The internal {@link CountDownTimer} is also cancelled in {@link #onDetachedFromWindow()}.
 *
 * <h3>XML attrs</h3>
 * <pre>{@code
 * <com.walkmate.core.designsystem.view.CountdownTimerView
 *     app:wm_urgentThresholdSec="60"
 *     app:wm_urgentColor="@color/color_danger"
 *     app:wm_normalColor="@color/text_muted" />
 * }</pre>
 */
public class CountdownTimerView extends AppCompatTextView {

    public interface OnExpiredListener {
        void onExpired();
    }

    // Default thresholds / colours (overridden by attrs)
    private static final int DEFAULT_URGENT_THRESHOLD_SEC = 60;

    private int urgentThresholdMs;
    private int urgentColor;
    private int normalColor;

    @Nullable private CountDownTimer activeTimer;
    @Nullable private OnExpiredListener expiredListener;

    // ─── Constructors ────────────────────────────────────────────────────────

    public CountdownTimerView(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public CountdownTimerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CountdownTimerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    // ─── Initialisation ──────────────────────────────────────────────────────

    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        // Resolve defaults first so they are always populated even if attrs is null.
        urgentThresholdMs = DEFAULT_URGENT_THRESHOLD_SEC * 1_000;
        urgentColor = ContextCompat.getColor(context, R.color.color_danger);
        normalColor = ContextCompat.getColor(context, R.color.text_muted);

        if (attrs == null) return;

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CountdownTimerView);
        try {
            int thresholdSec = a.getInt(
                    R.styleable.CountdownTimerView_wm_urgentThresholdSec,
                    DEFAULT_URGENT_THRESHOLD_SEC);
            urgentThresholdMs = thresholdSec * 1_000;

            urgentColor = a.getColor(
                    R.styleable.CountdownTimerView_wm_urgentColor,
                    urgentColor);

            normalColor = a.getColor(
                    R.styleable.CountdownTimerView_wm_normalColor,
                    normalColor);
        } finally {
            a.recycle();
        }
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Parse an ISO-8601 instant string (e.g. {@code "2026-04-09T12:30:00Z"}) and
     * start the countdown toward that expiry time.
     */
    public void startCountdown(@NonNull String expiresAtIso) {
        long epochMs = Instant.parse(expiresAtIso).toEpochMilli();
        startCountdown(epochMs);
    }

    /**
     * Start the countdown toward the given expiry time expressed as epoch milliseconds.
     * Cancels any running timer before starting (guard against re-bind in RecyclerView).
     */
    public void startCountdown(long expiresAtEpochMs) {
        cancelCountdown();

        long remaining = expiresAtEpochMs - System.currentTimeMillis();
        if (remaining <= 0) {
            markExpired();
            return;
        }

        setTextColor(normalColor);
        activeTimer = new CountDownTimer(remaining, 1_000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long mins = millisUntilFinished / 60_000L;
                long secs = (millisUntilFinished % 60_000L) / 1_000L;
                setText(String.format("%02d:%02d", mins, secs));
                setTextColor(millisUntilFinished <= urgentThresholdMs ? urgentColor : normalColor);
            }

            @Override
            public void onFinish() {
                markExpired();
            }
        };
        activeTimer.start();
    }

    /** Cancel the internal timer. Call from adapter's {@code onViewRecycled()}. */
    public void cancelCountdown() {
        if (activeTimer != null) {
            activeTimer.cancel();
            activeTimer = null;
        }
    }

    /** Register a listener that fires when the countdown reaches zero. */
    public void setOnExpiredListener(@Nullable OnExpiredListener listener) {
        this.expiredListener = listener;
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // Prevent leaked CountDownTimer when the view is recycled or the screen is popped.
        cancelCountdown();
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    private void markExpired() {
        activeTimer = null;
        setText("Expired");
        setTextColor(urgentColor);
        if (expiredListener != null) {
            expiredListener.onExpired();
        }
    }
}
