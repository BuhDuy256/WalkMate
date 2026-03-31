package com.walkmate.core.designsystem.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.walkmate.R;

/**
 * WalkMateInputField — compound view encapsulating the project's standard
 * labelled input pattern (label + edittext + optional password toggle + error line).
 *
 * <h3>Usage in XML</h3>
 * <pre>{@code
 * <com.walkmate.core.designsystem.view.WalkMateInputField
 *     android:id="@+id/field_email"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:layout_marginTop="20dp"
 *     app:wm_label="Email Address"
 *     app:wm_hint="hello@example.com"
 *     app:wm_icon="@drawable/ic_mail"
 *     app:wm_inputType="textEmailAddress" />
 *
 * <com.walkmate.core.designsystem.view.WalkMateInputField
 *     android:id="@+id/field_password"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:layout_marginTop="16dp"
 *     app:wm_label="Password"
 *     app:wm_hint="Enter your password"
 *     app:wm_icon="@drawable/ic_lock"
 *     app:wm_passwordToggle="true" />
 * }</pre>
 *
 * <h3>Usage in Fragment (renderState)</h3>
 * <pre>{@code
 * fieldEmail.setError(state.getEmailError());   // null clears the error
 * fieldPassword.setError(state.getPasswordError());
 * }</pre>
 *
 * <h3>Architecture contract</h3>
 * This view owns all input-field presentation logic:
 * <ul>
 *   <li>Password visibility toggle (boolean flag + cursor-end correction)</li>
 *   <li>Inline error display / clearing</li>
 *   <li>Icon assignment via {@code setCompoundDrawablesRelativeWithIntrinsicBounds}</li>
 * </ul>
 * Fragments must NOT re-implement any of the above. They call public API only.
 */
public class WalkMateInputField extends LinearLayout {

    private TextView tvLabel;
    private EditText etInput;
    private ImageView ivPasswordToggle;
    private TextView tvError;

    private boolean isPasswordVisible = false;
    private boolean isPasswordToggleEnabled = false;

    // ─── Constructors ────────────────────────────────────────────────────────

    public WalkMateInputField(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public WalkMateInputField(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public WalkMateInputField(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    // ─── Initialisation ──────────────────────────────────────────────────────

    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_walkmate_input_field, this, true);

        tvLabel = findViewById(R.id.tv_field_label);
        etInput = findViewById(R.id.et_field_input);
        ivPasswordToggle = findViewById(R.id.iv_password_toggle);
        tvError = findViewById(R.id.tv_field_error);

        applyAttributes(context, attrs);
        ivPasswordToggle.setOnClickListener(v -> togglePasswordVisibility());
    }

    private void applyAttributes(@NonNull Context context, @Nullable AttributeSet attrs) {
        if (attrs == null) return;

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.WalkMateInputField);
        try {
            String label = a.getString(R.styleable.WalkMateInputField_wm_label);
            String hint = a.getString(R.styleable.WalkMateInputField_wm_hint);
            int iconRes = a.getResourceId(R.styleable.WalkMateInputField_wm_icon, 0);
            // wm_inputType accepts the same integer values as android:inputType.
            // Default: TYPE_CLASS_TEXT (plain text, no special keyboard).
            int inputType = a.getInt(
                    R.styleable.WalkMateInputField_wm_inputType,
                    InputType.TYPE_CLASS_TEXT);
            boolean passwordToggle = a.getBoolean(
                    R.styleable.WalkMateInputField_wm_passwordToggle, false);

            if (label != null) setLabel(label);
            if (hint != null) setHint(hint);
            if (iconRes != 0) setIcon(iconRes);
            if (!passwordToggle) {
                // Only set inputType from XML when not a password field — password
                // fields get TYPE_TEXT_VARIATION_PASSWORD forced inside
                // setPasswordToggleEnabled() to guarantee correct masking.
                setInputType(inputType);
            }
            setPasswordToggleEnabled(passwordToggle);
        } finally {
            a.recycle();
        }
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Sets the label text shown above the field. */
    public void setLabel(String label) {
        tvLabel.setText(label);
    }

    /** Sets the placeholder hint inside the EditText. */
    public void setHint(String hint) {
        etInput.setHint(hint);
    }

    /**
     * Sets the start-compound drawable (icon) of the EditText.
     * Uses {@code setCompoundDrawablesRelativeWithIntrinsicBounds} to respect RTL.
     */
    public void setIcon(@DrawableRes int iconRes) {
        Drawable icon = ContextCompat.getDrawable(getContext(), iconRes);
        etInput.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
        // 12dp padding matches the drawablePadding in the login/register spec.
        int paddingPx = (int) (12 * getResources().getDisplayMetrics().density);
        etInput.setCompoundDrawablePadding(paddingPx);
    }

    /** Forwards input type to the inner EditText. */
    public void setInputType(int inputType) {
        etInput.setInputType(inputType);
    }

    /**
     * Shows or hides the password visibility toggle.
     * When enabled, forces {@code TYPE_TEXT_VARIATION_PASSWORD} and manages
     * masking state internally — callers do not need to handle this.
     */
    public void setPasswordToggleEnabled(boolean enabled) {
        isPasswordToggleEnabled = enabled;
        ivPasswordToggle.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (enabled) {
            // Force masked input; isPasswordVisible starts false (masked).
            etInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            etInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
        }
    }

    /** Programmatically sets the EditText value. */
    public void setText(String text) {
        etInput.setText(text);
    }

    /** Returns the current EditText value, never null. */
    @NonNull
    public String getText() {
        return etInput.getText() != null ? etInput.getText().toString() : "";
    }

    /**
     * Shows an inline error below the field. Pass {@code null} or an empty
     * string to clear the error (equivalent to calling {@link #clearError()}).
     */
    public void setError(@Nullable String error) {
        if (error != null && !error.isEmpty()) {
            tvError.setText(error);
            tvError.setVisibility(View.VISIBLE);
        } else {
            clearError();
        }
    }

    /** Hides the inline error label and clears its text. */
    public void clearError() {
        tvError.setVisibility(View.GONE);
        tvError.setText(null);
    }

    /**
     * Delegates to the inner EditText so callers can watch text changes without
     * reaching into the view's internals.
     */
    public void addTextChangedListener(TextWatcher watcher) {
        etInput.addTextChangedListener(watcher);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Toggles password masking. Updates the toggle icon and moves the cursor
     * to the end to prevent it jumping to position 0 after a transformation
     * method change (known Android quirk).
     */
    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;
        if (isPasswordVisible) {
            etInput.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            ivPasswordToggle.setImageResource(R.drawable.ic_eye_show);
        } else {
            etInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
            ivPasswordToggle.setImageResource(R.drawable.ic_eye_hide);
        }
        // Cursor must be reset after every transformation method change.
        if (etInput.getText() != null) {
            etInput.setSelection(etInput.getText().length());
        }
    }
}
