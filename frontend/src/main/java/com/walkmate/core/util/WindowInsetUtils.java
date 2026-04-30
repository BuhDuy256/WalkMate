package com.walkmate.core.util;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Helpers for applying window insets to pinned header / footer views.
 *
 * Call these in Fragment.onViewCreated() for any view that sits directly
 * against a system bar. The original XML padding is always preserved and
 * the system bar height is added on top of it, so re-dispatch on config
 * changes does not accumulate extra padding.
 */
public final class WindowInsetUtils {

    private WindowInsetUtils() {}

    /** Adds the status-bar height as extra top padding on a pinned header view. */
    public static void applyStatusBarPadding(View view) {
        int origTop = view.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top + origTop,
                    v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
    }

    /** Adds the navigation-bar height as extra bottom padding on a pinned footer view. */
    public static void applyNavBarPadding(View view) {
        int origBottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                    v.getPaddingRight(), bars.bottom + origBottom);
            return insets;
        });
    }
}
