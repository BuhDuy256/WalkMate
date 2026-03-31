package com.walkmate.core.designsystem.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.walkmate.R;

import java.util.List;

/**
 * TagChipGroup — {@link ChipGroup} subclass that creates chips from a
 * {@code List<String>} with a single {@link #setTags(List)} call.
 *
 * <p>Eliminates the boilerplate pattern repeated across four locations
 * ({@code ProposalAdapter.bindTagChips()}, {@code FindingAdapter},
 * {@code ExploreFragment} hotspot chips, {@code CreateIntentFragment}
 * selection chips):</p>
 * <pre>
 *   // Before — duplicated in every adapter/fragment:
 *   chipGroup.removeAllViews();
 *   for (String tag : tags) {
 *       Chip chip = new Chip(context);
 *       chip.setText(tag);
 *       chip.setChipBackgroundColorResource(R.color.orange_end);
 *       chip.setTextColor(Color.WHITE);
 *       chip.setTextSize(12);
 *       chip.setChipCornerRadius(999);
 *       chip.setChipStrokeWidth(0);
 *       chip.setClickable(false);
 *       chipGroup.addView(chip);
 *   }
 *
 *   // After — one line:
 *   chipGroup.setTags(tags);
 * </pre>
 *
 * <h3>Chip Styles</h3>
 * <ul>
 *   <li><b>DISPLAY</b> (default) — non-clickable, uses
 *       {@code Widget.WalkMate.Chip.Active} (orange bg, white text, 12sp).
 *       For read-only tag display in proposal/finding cards.</li>
 *   <li><b>SELECTABLE</b> — clickable toggle, uses {@code Widget.WalkMate.Chip}
 *       (selector-driven bg/text, 14sp). For filter UIs like CreateIntentFragment.</li>
 * </ul>
 *
 * <h3>Layout Note</h3>
 * No separate layout file is needed because TagChipGroup IS a ChipGroup — all
 * standard ChipGroup XML attributes ({@code app:chipSpacingHorizontal}, etc.)
 * continue to work as normal. This is a documented exception to the
 * "view_<name>.xml for every custom view" rule: when extending a concrete
 * Material component that is already a ViewGroup, no additional layout layer
 * is required.
 *
 * <h3>Usage in XML</h3>
 * <pre>{@code
 * <!-- Read-only tags in proposal card -->
 * <com.walkmate.core.designsystem.view.TagChipGroup
 *     android:id="@+id/chipGroupTags"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:layout_marginTop="6dp"
 *     app:chipSpacingHorizontal="6dp"
 *     app:chipSpacingVertical="4dp"
 *     app:wm_chipStyle="display" />
 *
 * <!-- Selectable filter chips in CreateIntentFragment -->
 * <com.walkmate.core.designsystem.view.TagChipGroup
 *     android:id="@+id/chipGroupInterests"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:wm_chipStyle="selectable"
 *     app:singleSelection="false" />
 * }</pre>
 *
 * <h3>Usage at runtime</h3>
 * <pre>{@code
 * // In adapter bind() — no more manual chip creation:
 * chipGroupTags.setTags(proposal.getCommonInterests());
 *
 * // Selectable style override at runtime:
 * chipGroupInterests.setChipStyleSelectable(true);
 * chipGroupInterests.setTags(Arrays.asList("Running", "Coffee", "Nature"));
 * }</pre>
 */
public class TagChipGroup extends ChipGroup {

    /** Chip style constants matching the {@code wm_chipStyle} enum attr. */
    public static final int CHIP_STYLE_DISPLAY    = 0;
    public static final int CHIP_STYLE_SELECTABLE = 1;

    private int chipStyle = CHIP_STYLE_DISPLAY;

    // ─── Constructors ────────────────────────────────────────────────────────

    public TagChipGroup(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public TagChipGroup(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public TagChipGroup(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    // ─── Initialisation ──────────────────────────────────────────────────────

    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        if (attrs == null) return;

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TagChipGroup);
        try {
            chipStyle = a.getInt(R.styleable.TagChipGroup_wm_chipStyle, CHIP_STYLE_DISPLAY);
        } finally {
            a.recycle();
        }
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Replaces all current chips with new chips created from {@code tags}.
     * Handles {@code removeAllViews()} internally — callers must NOT call it.
     *
     * <p>Null and empty lists clear all chips without creating new ones.</p>
     *
     * @param tags List of tag strings; must not contain null elements.
     */
    public void setTags(@Nullable List<String> tags) {
        removeAllViews();
        if (tags == null || tags.isEmpty()) return;

        for (String tag : tags) {
            addView(createChip(tag));
        }
    }

    /**
     * Switches the chip style at runtime.
     *
     * @param selectable {@code true} for selectable toggle chips,
     *                   {@code false} for non-clickable display chips.
     */
    public void setChipStyleSelectable(boolean selectable) {
        chipStyle = selectable ? CHIP_STYLE_SELECTABLE : CHIP_STYLE_DISPLAY;
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Creates a single {@link Chip} for the given tag text using the current
     * {@link #chipStyle}.
     *
     * <p>Chips are created via {@link ContextThemeWrapper} — the recommended
     * pattern for applying Material XML styles programmatically. This avoids
     * manually setting every chip property (corner radius, padding, text color,
     * etc.) and ensures future style updates automatically propagate.</p>
     */
    @NonNull
    private Chip createChip(@NonNull String tag) {
        int themeResId = (chipStyle == CHIP_STYLE_SELECTABLE)
                ? R.style.Widget_WalkMate_Chip
                : R.style.Widget_WalkMate_Chip_Active;

        Chip chip = new Chip(new ContextThemeWrapper(getContext(), themeResId));
        chip.setText(tag);

        if (chipStyle == CHIP_STYLE_DISPLAY) {
            // Display chips are purely informational — disable all interaction.
            chip.setClickable(false);
            chip.setFocusable(false);
            chip.setCheckable(false);
        }
        // Selectable chips retain ChipGroup's toggle/single-selection behaviour.

        return chip;
    }
}
