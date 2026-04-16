package com.walkmate.core.designsystem.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.walkmate.R;

/**
 * ActivationWindowButtonView — shows the "I'm Here!" button together with a
 * status label.
 *
 * <p>The activation button is intentionally always enabled when bound from a
 * PENDING session card. The 5-minute rule belongs to tracking completion, not
 * to this activation entry point.</p>
 *
 * <h3>Required lifecycle calls</h3>
 * <ul>
 *   <li>Call {@link #bind(String, View.OnClickListener)} from the Fragment /
 *       adapter once the session card is ready.</li>
 *   <li>Call {@link #release()} from {@code onDestroyView()} or
 *       {@code onViewRecycled()} for API compatibility with previous versions.</li>
 * </ul>
 *
 * <h3>XML attrs</h3>
 * <pre>{@code
 * <com.walkmate.core.designsystem.view.ActivationWindowButtonView
 *     app:wm_arriveLabel="Start Your Walk"
 *     app:wm_waitingLabel="Not yet open" />
 * }</pre>
 */
public class ActivationWindowButtonView extends LinearLayout {

    private TextView       tvStatus;
    private WalkMateButton btnArrive;

    private String arriveLabel  = "Start Your Walk";

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
        } finally {
            a.recycle();
        }
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Wire the view to an arrive handler.
     *
     * @param scheduledStartIso kept for API compatibility; no longer used
     * @param onArrivedClick    click listener for the "I'm Here!" button
     */
    public void bind(@NonNull String scheduledStartIso, @NonNull View.OnClickListener onArrivedClick) {
        btnArrive.setOnClickListener(onArrivedClick);
        tvStatus.setText(arriveLabel);
        btnArrive.setEnabled(true);
    }

    /**
     * No-op kept for backward compatibility with adapter lifecycle calls.
     */
    public void release() {
        // no-op
    }
}
