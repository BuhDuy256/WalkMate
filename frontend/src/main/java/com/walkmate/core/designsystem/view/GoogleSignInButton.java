package com.walkmate.core.designsystem.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.walkmate.R;

/**
 * GoogleSignInButton — standardised "Continue with Google" button.
 *
 * Encapsulates the MaterialButton styling (white fill, Google icon, pill shape,
 * grey stroke) so it is declared once here instead of duplicated across every
 * auth screen.  No XML-configurable attributes — text, icon, and colours are
 * fixed in {@code view_google_signin_button.xml}.
 *
 * <h3>Usage in XML</h3>
 * <pre>{@code
 * <com.walkmate.core.designsystem.view.GoogleSignInButton
 *     android:id="@+id/btn_google_signin"
 *     android:layout_width="match_parent"
 *     android:layout_height="52dp"
 *     android:layout_marginTop="16dp" />
 * }</pre>
 *
 * <h3>Usage in Activity</h3>
 * <pre>{@code
 * btnGoogleSignIn.setEnabled(false);           // while waiting for result
 * btnGoogleSignIn.setEnabled(true);            // on result / error
 * btnGoogleSignIn.setOnClickListener(v -> …); // forwarded to inner button
 * }</pre>
 */
public class GoogleSignInButton extends FrameLayout {

    private MaterialButton btnGoogle;

    public GoogleSignInButton(@NonNull Context context) {
        super(context);
        init(context);
    }

    public GoogleSignInButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GoogleSignInButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        inflate(context, R.layout.view_google_signin_button, this);
        btnGoogle = findViewById(R.id.btn_google);
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener listener) {
        btnGoogle.setOnClickListener(listener);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        btnGoogle.setEnabled(enabled);
        btnGoogle.setAlpha(enabled ? 1.0f : 0.5f);
    }
}
