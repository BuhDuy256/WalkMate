package com.walkmate.core.designsystem.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ContentLoadingProgressBar;

import com.walkmate.R;

/**
 * WalkMateButton — compound view encapsulating the project's two standard
 * button variants and their shared loading-state logic.
 *
 * <h3>Variants</h3>
 * <ul>
 *   <li><b>FILLED</b> (default) — gradient orange pill, white bold text.
 *       Matches {@code bg_gradient_orange_pill} + white text used throughout.</li>
 *   <li><b>OUTLINED</b> — transparent fill, 2dp orange stroke pill, orange text.
 *       Matches {@code bg_button_outline_orange} used for secondary actions.</li>
 * </ul>
 *
 * <h3>Loading State</h3>
 * {@code setLoading(true)} disables the button, hides its text, and shows an
 * indeterminate {@link ProgressBar} centered in the button area. The spinner
 * is white for FILLED buttons (readable on orange) and orange for OUTLINED.
 * {@code setLoading(false)} restores text and re-enables interaction.
 *
 * <h3>Usage in XML</h3>
 * <pre>{@code
 * <!-- Primary action button -->
 * <com.walkmate.core.designsystem.view.WalkMateButton
 *     android:id="@+id/btn_sign_in"
 *     android:layout_width="match_parent"
 *     android:layout_height="56dp"
 *     android:layout_marginTop="24dp"
 *     app:wm_buttonStyle="filled"
 *     app:wm_text="Sign In ✦" />
 *
 * <!-- Secondary / pass button -->
 * <com.walkmate.core.designsystem.view.WalkMateButton
 *     android:id="@+id/btn_pass"
 *     android:layout_width="0dp"
 *     android:layout_height="48dp"
 *     android:layout_weight="1"
 *     app:wm_buttonStyle="outlined"
 *     app:wm_text="@string/btn_pass" />
 * }</pre>
 *
 * <h3>Usage in Fragment (renderState)</h3>
 * <pre>{@code
 * btnSignIn.setLoading(state.isLoading());
 * }</pre>
 */
public class WalkMateButton extends FrameLayout {

    /** Visual style constants matching the {@code wm_buttonStyle} enum attr. */
    public static final int STYLE_FILLED   = 0;
    public static final int STYLE_OUTLINED = 1;
    public static final int STYLE_GHOST    = 2;

    private AppCompatButton btnAction;
    private ProgressBar pbLoading;

    private int buttonStyle = STYLE_FILLED;
    private String storedText = "";
    private boolean isLoading = false;

    // ─── Constructors ────────────────────────────────────────────────────────

    public WalkMateButton(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public WalkMateButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public WalkMateButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    // ─── Initialisation ──────────────────────────────────────────────────────

    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_walkmate_button, this, true);

        btnAction = findViewById(R.id.btn_action);
        pbLoading = findViewById(R.id.pb_loading);

        applyAttributes(context, attrs);
    }

    private void applyAttributes(@NonNull Context context, @Nullable AttributeSet attrs) {
        if (attrs == null) {
            applyStyle(context, STYLE_FILLED);
            return;
        }

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.WalkMateButton);
        try {
            buttonStyle = a.getInt(R.styleable.WalkMateButton_wm_buttonStyle, STYLE_FILLED);
            String text = a.getString(R.styleable.WalkMateButton_wm_text);
            if (text != null) {
                storedText = text;
                btnAction.setText(text);
            }
        } finally {
            a.recycle();
        }

        applyStyle(context, buttonStyle);
    }

    /**
     * Applies the visual variant (background drawable, text color, spinner tint).
     * Separated from applyAttributes() so callers can switch styles at runtime.
     */
    private void applyStyle(@NonNull Context context, int style) {
        if (style == STYLE_OUTLINED) {
            btnAction.setBackgroundResource(R.drawable.bg_button_outline_orange);
            btnAction.setTextColor(ContextCompat.getColor(context, R.color.orange_primary));
            pbLoading.setIndeterminateTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.orange_primary)));
        } else if (style == STYLE_GHOST) {
            btnAction.setBackgroundResource(R.drawable.bg_button_ghost);
            btnAction.setTextColor(ContextCompat.getColor(context, R.color.text_label));
            pbLoading.setIndeterminateTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_label)));
        } else {
            // FILLED (default)
            btnAction.setBackgroundResource(R.drawable.bg_gradient_orange_pill);
            btnAction.setTextColor(ContextCompat.getColor(context, R.color.white));
            pbLoading.setIndeterminateTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white)));
        }
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Sets the button label text. Safe to call at any time, including during
     * a loading state — the text is stored and restored when loading ends.
     */
    public void setText(String text) {
        storedText = text != null ? text : "";
        if (!isLoading) {
            btnAction.setText(storedText);
        }
    }

    /**
     * Switches between loading and ready states.
     *
     * <ul>
     *   <li>{@code true} — hides button text, shows spinner, disables interaction.</li>
     *   <li>{@code false} — restores button text, hides spinner, re-enables interaction.</li>
     * </ul>
     */
    public void setLoading(boolean loading) {
        isLoading = loading;
        if (loading) {
            btnAction.setText("");
            btnAction.setEnabled(false);
            pbLoading.setVisibility(View.VISIBLE);
        } else {
            btnAction.setText(storedText);
            btnAction.setEnabled(true);
            pbLoading.setVisibility(View.GONE);
        }
    }

    /** Returns {@code true} if the button is currently in the loading state. */
    public boolean isLoading() {
        return isLoading;
    }

    /**
     * Switches the visual variant at runtime.
     *
     * @param style {@link #STYLE_FILLED} or {@link #STYLE_OUTLINED}
     */
    public void setButtonStyle(int style) {
        buttonStyle = style;
        applyStyle(getContext(), style);
    }

    /**
     * Forwards the click listener to the inner {@link AppCompatButton} so
     * callers do not need to reach inside the FrameLayout.
     */
    @Override
    public void setOnClickListener(@Nullable OnClickListener listener) {
        btnAction.setOnClickListener(listener);
    }

    /** Programmatically enables or disables the button (distinct from loading state). */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        btnAction.setEnabled(enabled && !isLoading);
        btnAction.setAlpha(enabled ? 1.0f : 0.5f);
    }
}
