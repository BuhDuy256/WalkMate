package com.walkmate.core.designsystem.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.walkmate.R;

/**
 * AvatarInitialView — compound view that renders a circular avatar using one
 * of two strategies depending on available data:
 *
 * <ol>
 *   <li><b>Photo URL present:</b> loads the image via Glide with {@code circleCrop()}.
 *       While the network request is in flight, the initial-letter fallback remains
 *       visible through the transparent Glide placeholder. On load success the photo
 *       covers the initial; on load failure the initial remains visible.</li>
 *   <li><b>No URL:</b> shows the first character of the name (uppercased) centered in
 *       bold orange on the warm-circle background.</li>
 * </ol>
 *
 * <p>An optional green online-status dot can be toggled via {@link #setOnlineStatus(boolean)}
 * or the {@link #bind(String, String, boolean)} overload.</p>
 *
 * <h3>Usage in XML (static, design-time preview)</h3>
 * <pre>{@code
 * <com.walkmate.core.designsystem.view.AvatarInitialView
 *     android:id="@+id/avatar"
 *     android:layout_width="52dp"
 *     android:layout_height="52dp"
 *     app:wm_avatarName="Thu Hà"
 *     app:wm_showOnlineStatus="true" />
 * }</pre>
 *
 * <h3>Usage at runtime (ViewModel-driven)</h3>
 * <pre>{@code
 * // In adapter bind() or Fragment renderState():
 * avatar.bind(user.getName(), user.getPhotoUrl());            // photo or initial
 * avatar.bind(user.getName(), user.getPhotoUrl(), isOnline);  // with online dot
 * }</pre>
 *
 * <h3>Architecture contract</h3>
 * This view owns ALL avatar-display logic including Glide lifecycle. Fragments
 * and Adapters must NOT import Glide directly for circular avatar loading.
 * Use {@code GlideHelper} for non-circle image loads.
 */
public class AvatarInitialView extends FrameLayout {

    private TextView tvInitial;
    private ImageView imgAvatar;
    private View viewOnlineDot;

    // ─── Constructors ────────────────────────────────────────────────────────

    public AvatarInitialView(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public AvatarInitialView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public AvatarInitialView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    // ─── Initialisation ──────────────────────────────────────────────────────

    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        // bg_warm_circle provides the round warm-cream background behind the initial.
        setBackground(ContextCompat.getDrawable(context, R.drawable.bg_warm_circle));

        LayoutInflater.from(context).inflate(R.layout.view_avatar_initial, this, true);

        tvInitial     = findViewById(R.id.tv_initial);
        imgAvatar     = findViewById(R.id.img_avatar);
        viewOnlineDot = findViewById(R.id.view_online_dot);

        applyAttributes(context, attrs);
    }

    private void applyAttributes(@NonNull Context context, @Nullable AttributeSet attrs) {
        if (attrs == null) return;

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AvatarInitialView);
        try {
            String name = a.getString(R.styleable.AvatarInitialView_wm_avatarName);
            boolean showOnline = a.getBoolean(
                    R.styleable.AvatarInitialView_wm_showOnlineStatus, false);
            float textSizeSp = a.getDimension(
                    R.styleable.AvatarInitialView_wm_initialTextSize, -1);

            if (name != null) {
                tvInitial.setText(extractInitial(name));
            }
            setOnlineStatus(showOnline);
            if (textSizeSp > 0) {
                // getDimension returns px; convert to sp for setText setTextSize(COMPLEX_UNIT_PX)
                tvInitial.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSizeSp);
            }
        } finally {
            a.recycle();
        }
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Binds the view to a user's name and photo URL.
     *
     * @param name     Display name — first character is used as fallback initial.
     *                 Must not be null.
     * @param photoUrl Remote image URL. Pass {@code null} or empty to show initial only.
     */
    public void bind(@NonNull String name, @Nullable String photoUrl) {
        bind(name, photoUrl, false);
    }

    /**
     * Binds the view to a user's name, photo URL, and online status.
     *
     * @param name     Display name.
     * @param photoUrl Remote image URL; null/empty shows the initial fallback.
     * @param isOnline Whether to show the green online-status dot.
     */
    public void bind(@NonNull String name, @Nullable String photoUrl, boolean isOnline) {
        tvInitial.setText(extractInitial(name != null ? name : ""));
        loadPhoto(photoUrl);
        setOnlineStatus(isOnline);
    }

    /**
     * Shows or hides the green online-status dot (bottom-right corner).
     * Safe to call independently of {@link #bind}.
     */
    public void setOnlineStatus(boolean online) {
        viewOnlineDot.setVisibility(online ? View.VISIBLE : View.GONE);
    }

    /**
     * Overrides the initial-letter text size in sp.
     * Useful when the avatar is significantly larger or smaller than the 48dp default
     * (e.g., 88dp profile photo → call {@code setInitialTextSizeSp(32)}).
     */
    public void setInitialTextSizeSp(float sp) {
        tvInitial.setTextSize(sp);
    }

    /**
     * Overrides the initial-letter text color.
     * Use when the avatar background is changed from the default warm circle
     * (e.g., solid orange background requires white text for contrast).
     */
    public void setInitialTextColor(int color) {
        tvInitial.setTextColor(color);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Extracts the uppercase first character of {@code name} as the initial letter.
     * Falls back to "?" for null, empty, or whitespace-only names.
     */
    @NonNull
    private String extractInitial(@NonNull String name) {
        if (name == null) return "?";
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "?";
        return String.valueOf(Character.toUpperCase(trimmed.charAt(0)));
    }

    /**
     * Loads the photo URL into the ImageView with Glide's {@code circleCrop()}.
     *
     * Strategy:
     * - URL present → make ImageView VISIBLE, load with transparent placeholder
     *   (initial remains visible through the ImageView while loading, photo
     *   covers it on success; initial remains if network fails).
     * - URL absent → keep ImageView INVISIBLE so the initial is visible.
     */
    private void loadPhoto(@Nullable String photoUrl) {
        if (photoUrl != null && !photoUrl.isEmpty()) {
            imgAvatar.setVisibility(View.VISIBLE);
            Glide.with(getContext())
                    .load(photoUrl)
                    .circleCrop()
                    .placeholder(android.R.color.transparent)
                    .error(android.R.color.transparent)
                    .into(imgAvatar);
        } else {
            // Cancel any pending request and hide the ImageView so the initial shows.
            Glide.with(getContext()).clear(imgAvatar);
            imgAvatar.setVisibility(View.INVISIBLE);
        }
    }
}
