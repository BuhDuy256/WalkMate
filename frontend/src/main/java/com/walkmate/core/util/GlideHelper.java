package com.walkmate.core.util;

import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.walkmate.R;

/**
 * GlideHelper — single source of truth for all Glide image-loading calls
 * in WalkMate.
 *
 * <h3>Problem Solved</h3>
 * <p>Before this class, 6+ locations in Fragments and Adapters all duplicated:</p>
 * <pre>
 *   if (url != null &amp;&amp; !url.isEmpty()) {
 *       Glide.with(context).load(url).circleCrop()
 *            .placeholder(R.drawable.ic_user).into(imageView);
 *   } else {
 *       imageView.setImageResource(R.drawable.ic_user);
 *   }
 * </pre>
 * <p>Any change to Glide configuration (timeout, cache strategy, error drawable,
 * future migration to Coil/Picasso) had to be made in every call site.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Circular crop (avatars, profile photos)
 * GlideHelper.loadCircle(imageView, user.getPhotoUrl());
 *
 * // Circular crop with custom placeholder
 * GlideHelper.loadCircle(imageView, url, R.drawable.ic_placeholder_dog);
 *
 * // Rounded corners (banner / card images)
 * GlideHelper.loadRounded(imageView, url, 12);  // 12dp radius
 *
 * // Cancel an in-flight request (call in onDestroyView / ViewHolder.recycle)
 * GlideHelper.cancel(imageView);
 * }</pre>
 *
 * <h3>Architecture contract</h3>
 * <ul>
 *   <li>Only this class and {@link com.walkmate.core.designsystem.view.AvatarInitialView}
 *       may import {@code com.bumptech.glide.*}.</li>
 *   <li>{@code AvatarInitialView} handles circular avatar loading with the
 *       initial-letter fallback directly; use it instead of GlideHelper when
 *       the fallback behavior is needed.</li>
 *   <li>All other ImageView loads go through this helper.</li>
 * </ul>
 *
 * <h3>Migration note</h3>
 * If the project ever migrates from Glide to Coil or Picasso, only this file
 * (plus {@code AvatarInitialView.loadPhoto()}) needs to change.
 */
public final class GlideHelper {

    @DrawableRes
    private static final int DEFAULT_PLACEHOLDER = R.drawable.ic_user;

    /** Utility class — no instances. */
    private GlideHelper() {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Loads {@code url} into {@code view} with a circular crop and the default
     * placeholder ({@code ic_user}). Shows the placeholder when the URL is null,
     * empty, or the load fails.
     *
     * @param view The target ImageView. Must not be null.
     * @param url  Remote image URL. Null or empty shows the placeholder directly.
     */
    public static void loadCircle(@NonNull ImageView view, @Nullable String url) {
        loadCircle(view, url, DEFAULT_PLACEHOLDER);
    }

    /**
     * Loads {@code url} into {@code view} with a circular crop and a custom
     * placeholder drawable.
     *
     * @param view        The target ImageView.
     * @param url         Remote image URL; null/empty shows {@code placeholder}.
     * @param placeholder Drawable res shown while loading and on error.
     */
    public static void loadCircle(@NonNull ImageView view,
                                  @Nullable String url,
                                  @DrawableRes int placeholder) {
        if (url != null && !url.isEmpty()) {
            Glide.with(view.getContext())
                    .load(url)
                    .circleCrop()
                    .placeholder(placeholder)
                    .error(placeholder)
                    .into(view);
        } else {
            view.setImageResource(placeholder);
        }
    }

    /**
     * Loads {@code url} into {@code view} with rounded corners.
     *
     * <p>Suitable for card banner images, session background photos, and any
     * non-circle image that needs corner rounding.</p>
     *
     * @param view     The target ImageView.
     * @param url      Remote image URL; null/empty shows the default placeholder.
     * @param radiusDp Corner radius in density-independent pixels.
     */
    public static void loadRounded(@NonNull ImageView view,
                                   @Nullable String url,
                                   int radiusDp) {
        int radiusPx = dpToPx(view, radiusDp);
        if (url != null && !url.isEmpty()) {
            Glide.with(view.getContext())
                    .load(url)
                    .apply(RequestOptions.bitmapTransform(new RoundedCorners(radiusPx)))
                    .placeholder(DEFAULT_PLACEHOLDER)
                    .error(DEFAULT_PLACEHOLDER)
                    .into(view);
        } else {
            view.setImageResource(DEFAULT_PLACEHOLDER);
        }
    }

    /**
     * Loads {@code url} into {@code view} with no transformation (original crop/scale
     * is determined by the ImageView's {@code scaleType}).
     *
     * @param view        The target ImageView.
     * @param url         Remote image URL; null/empty shows {@code placeholder}.
     * @param placeholder Drawable res shown while loading and on error.
     */
    public static void load(@NonNull ImageView view,
                            @Nullable String url,
                            @DrawableRes int placeholder) {
        if (url != null && !url.isEmpty()) {
            Glide.with(view.getContext())
                    .load(url)
                    .placeholder(placeholder)
                    .error(placeholder)
                    .into(view);
        } else {
            view.setImageResource(placeholder);
        }
    }

    /**
     * Cancels any in-flight Glide request for {@code view} and clears the target.
     *
     * <p>Call this in {@code onDestroyView()} or {@code ViewHolder.recycle()} to
     * prevent Glide from writing to a detached or recycled view after a network
     * response arrives late.</p>
     *
     * @param view The ImageView whose pending request should be cancelled.
     */
    public static void cancel(@NonNull ImageView view) {
        Glide.with(view.getContext()).clear(view);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private static int dpToPx(@NonNull ImageView view, int dp) {
        float density = view.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
