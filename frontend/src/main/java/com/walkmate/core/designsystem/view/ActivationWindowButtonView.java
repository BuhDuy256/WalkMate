package com.walkmate.core.designsystem.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.walkmate.R;

import java.time.Instant;

/**
 * ActivationWindowButtonView — shows the "I'm Here!" button together with a
 * status label, enabling/disabling the button based on whether the current
 * time falls inside the activation window (scheduledStart − 10 min to
 * scheduledStart + 15 min, per spec invariant S-3).
 *
 * <p>Re-evaluates every 60 seconds via a {@link Handler#postDelayed} loop so
 * the button enables itself automatically once the window opens without
 * requiring a screen refresh from the Fragment.</p>
 *
 * <h3>Required lifecycle calls</h3>
 * <ul>
 *   <li>Call {@link #bind(String, View.OnClickListener)} from the Fragment /
 *       adapter once you have the session's scheduled start time.</li>
 *   <li>Call {@link #release()} from {@code onDestroyView()} or
 *       {@code onViewRecycled()} to stop the re-evaluation loop.</li>
 * </ul>
 *
 * <h3>XML attrs</h3>
 * <pre>{@code
 * <com.walkmate.core.designsystem.view.ActivationWindowButtonView
 *     app:wm_arriveLabel="I'm Here!"
 *     app:wm_waitingLabel="Not yet open" />
 * }</pre>
 */
public class ActivationWindowButtonView extends LinearLayout {

    private static final long WINDOW_OPEN_OFFSET_MS  = 10L * 60_000L; // 10 min before
    private static final long WINDOW_CLOSE_OFFSET_MS = 15L * 60_000L; // 15 min after
    private static final long RE_EVAL_INTERVAL_MS    = 60_000L;        // re-check every 60 s

    private TextView       tvStatus;
    private WalkMateButton btnArrive;

    private String arriveLabel  = "I'm Here!";
    private String waitingLabel = "Not yet open";
    private String closedLabel  = "Window closed";

    private long windowOpenMs;
    private long windowCloseMs;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable reEvalRunnable = this::evaluate;

    // ─── Constructors ────────────────────────────────────────────────────────

    public ActivationWindowButtonView(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public ActivationWindowButtonView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public ActivationWindowButtonView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    // ─── Initialisation ──────────────────────────────────────────────────────

    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_activation_window_button, this, true);

        tvStatus  = findViewById(R.id.tv_activation_status);
        btnArrive = findViewById(R.id.btn_arrive);

        if (attrs == null) return;

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ActivationWindowButtonView);
        try {
            String attr = a.getString(R.styleable.ActivationWindowButtonView_wm_arriveLabel);
            if (attr != null) arriveLabel = attr;

            attr = a.getString(R.styleable.ActivationWindowButtonView_wm_waitingLabel);
            if (attr != null) waitingLabel = attr;
        } finally {
            a.recycle();
        }
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Wire the view to a session's scheduled start time and the arrive handler.
     * Immediately evaluates the window and schedules periodic re-evaluation.
     *
     * @param scheduledStartIso ISO-8601 instant of the session's scheduled start
     * @param onArrivedClick    click listener for the "I'm Here!" button
     */
    public void bind(@NonNull String scheduledStartIso, @NonNull View.OnClickListener onArrivedClick) {
        release(); // cancel any previous loop before rebinding

        long epochMs = Instant.parse(scheduledStartIso).toEpochMilli();
        windowOpenMs  = epochMs - WINDOW_OPEN_OFFSET_MS;
        windowCloseMs = epochMs + WINDOW_CLOSE_OFFSET_MS;

        btnArrive.setOnClickListener(onArrivedClick);
        evaluate();
    }

    /**
     * Stop the re-evaluation loop. Must be called from {@code onDestroyView()}
     * or the adapter's {@code onViewRecycled()} to prevent Handler leaks.
     */
    public void release() {
        handler.removeCallbacks(reEvalRunnable);
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    private void evaluate() {
        long now = System.currentTimeMillis();

        if (now < windowOpenMs) {
            // Too early — window not yet open
            tvStatus.setText(waitingLabel);
            btnArrive.setEnabled(false);
            scheduleNextEval();

        } else if (now <= windowCloseMs) {
            // Inside the activation window — enable the button
            tvStatus.setText(arriveLabel);
            btnArrive.setEnabled(true);
            scheduleNextEval();

        } else {
            // Past the window — disable permanently and stop re-evaluating
            tvStatus.setText(closedLabel);
            btnArrive.setEnabled(false);
            // No further postDelayed — release() is a no-op here but we still
            // remove any residual callbacks to be safe.
            release();
        }
    }

    private void scheduleNextEval() {
        handler.removeCallbacks(reEvalRunnable); // avoid double-posting
        handler.postDelayed(reEvalRunnable, RE_EVAL_INTERVAL_MS);
    }
}
