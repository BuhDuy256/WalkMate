package com.walkmate.core.designsystem.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.walkmate.R;

/**
 * WalkMateStatColumn — compound view for a single vertical stat column:
 *
 * <pre>
 *   [icon / emoji]     ← optional; exactly one is VISIBLE when set
 *   [value]            ← bold, prominent (default 18sp; override via wm_statValueSize)
 *   [label]            ← muted, centered (10sp)
 * </pre>
 *
 * <p>Replaces the duplicated pattern across three screens:</p>
 * <ul>
 *   <li>Home stats row (distance / sessions / day streak cards)</li>
 *   <li>Profile 3-column stats (Total KM | Sessions | Day Streak)</li>
 *   <li>Tracking screen columns (distance | duration | pace)</li>
 * </ul>
 *
 * <h3>Usage in XML (static preview)</h3>
 * <pre>{@code
 * <com.walkmate.core.designsystem.view.WalkMateStatColumn
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content"
 *     app:wm_statIcon="@drawable/ic_distance"
 *     app:wm_statValue="12.5"
 *     app:wm_statLabel="km this week" />
 *
 * <com.walkmate.core.designsystem.view.WalkMateStatColumn
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content"
 *     app:wm_statEmoji="🔥"
 *     app:wm_statValue="5"
 *     app:wm_statLabel="day streak"
 *     app:wm_statValueSize="26sp" />
 * }</pre>
 *
 * <h3>Usage at runtime</h3>
 * <pre>{@code
 * // Value + label only (no icon/emoji)
 * statDistance.bind("12.5", "km this week");
 *
 * // With drawable icon
 * statDistance.bind("12.5", "km this week", R.drawable.ic_distance);
 *
 * // With emoji
 * statStreak.bind("5", "day streak", "🔥");
 * }</pre>
 */
public class WalkMateStatColumn extends LinearLayout {

    private ImageView imgStatIcon;
    private TextView tvStatEmoji;
    private TextView tvStatValue;
    private TextView tvStatLabel;

    // ─── Constructors ────────────────────────────────────────────────────────

    public WalkMateStatColumn(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public WalkMateStatColumn(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public WalkMateStatColumn(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    // ─── Initialisation ──────────────────────────────────────────────────────

    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        setOrientation(VERTICAL);
        setGravity(android.view.Gravity.CENTER_HORIZONTAL);

        LayoutInflater.from(context).inflate(R.layout.view_walkmate_stat_column, this, true);

        imgStatIcon = findViewById(R.id.img_stat_icon);
        tvStatEmoji = findViewById(R.id.tv_stat_emoji);
        tvStatValue = findViewById(R.id.tv_stat_value);
        tvStatLabel = findViewById(R.id.tv_stat_label);

        applyAttributes(context, attrs);
    }

    private void applyAttributes(@NonNull Context context, @Nullable AttributeSet attrs) {
        if (attrs == null) return;

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.WalkMateStatColumn);
        try {
            String value    = a.getString(R.styleable.WalkMateStatColumn_wm_statValue);
            String label    = a.getString(R.styleable.WalkMateStatColumn_wm_statLabel);
            int iconRes     = a.getResourceId(R.styleable.WalkMateStatColumn_wm_statIcon, 0);
            String emoji    = a.getString(R.styleable.WalkMateStatColumn_wm_statEmoji);
            float valueSizePx = a.getDimension(R.styleable.WalkMateStatColumn_wm_statValueSize, -1);

            if (value != null) tvStatValue.setText(value);
            if (label != null) tvStatLabel.setText(label);
            if (iconRes != 0)  setIcon(iconRes);
            if (emoji != null) setEmoji(emoji);
            if (valueSizePx > 0) {
                tvStatValue.setTextSize(TypedValue.COMPLEX_UNIT_PX, valueSizePx);
            }
        } finally {
            a.recycle();
        }
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Binds value and label text only (no icon or emoji graphic).
     * Hides any previously set icon or emoji.
     */
    public void bind(@NonNull String value, @NonNull String label) {
        tvStatValue.setText(value);
        tvStatLabel.setText(label);
        clearGraphic();
    }

    /**
     * Binds value and label with a drawable icon above the value.
     * Hides emoji if previously set.
     */
    public void bind(@NonNull String value, @NonNull String label, @DrawableRes int iconRes) {
        bind(value, label);
        setIcon(iconRes);
    }

    /**
     * Binds value and label with an emoji character above the value.
     * Hides drawable icon if previously set.
     */
    public void bind(@NonNull String value, @NonNull String label, @NonNull String emoji) {
        bind(value, label);
        setEmoji(emoji);
    }

    /**
     * Sets a drawable icon above the value text.
     * Hides the emoji TextView. Pass {@code 0} to clear.
     */
    public void setIcon(@DrawableRes int iconRes) {
        tvStatEmoji.setVisibility(View.GONE);
        if (iconRes != 0) {
            imgStatIcon.setImageResource(iconRes);
            imgStatIcon.setVisibility(View.VISIBLE);
        } else {
            imgStatIcon.setVisibility(View.GONE);
        }
    }

    /**
     * Sets an emoji character above the value text.
     * Hides the icon ImageView. Pass {@code null} to clear.
     */
    public void setEmoji(@Nullable String emoji) {
        imgStatIcon.setVisibility(View.GONE);
        if (emoji != null && !emoji.isEmpty()) {
            tvStatEmoji.setText(emoji);
            tvStatEmoji.setVisibility(View.VISIBLE);
        } else {
            tvStatEmoji.setVisibility(View.GONE);
        }
    }

    /** Updates the value text without touching the label or graphic. */
    public void setValue(@NonNull String value) {
        tvStatValue.setText(value);
    }

    /** Updates the label text without touching the value or graphic. */
    public void setLabel(@NonNull String label) {
        tvStatLabel.setText(label);
    }

    /**
     * Overrides the value text size in sp.
     * Default is 18sp (Home/Profile). Pass 26sp for the Tracking screen columns.
     */
    public void setValueTextSizeSp(float sp) {
        tvStatValue.setTextSize(sp);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void clearGraphic() {
        imgStatIcon.setVisibility(View.GONE);
        tvStatEmoji.setVisibility(View.GONE);
    }
}
