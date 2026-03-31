package com.walkmate.core.designsystem.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.walkmate.R;

/**
 * WalkMateCardHeader — compound view for the standard card section header row:
 *
 * <pre>
 *   [emoji]  [title — — — — — — — — — — — — — — — — — — ]  [chevron?]
 * </pre>
 *
 * <h3>Why ConstraintLayout over the LinearLayout + weight-spacer pattern</h3>
 * <p>The legacy implementation uses:</p>
 * <pre>
 *   LinearLayout (horizontal, gravity: center_vertical) {
 *     TextView emoji    (marginEnd="8dp")
 *     TextView title    (layout_weight="1")   ← forces double measure pass
 *     ImageView chevron (20dp)
 *   }
 * </pre>
 * <p>Every {@code layout_weight} child is measured twice by Android's layout engine:
 * once to calculate remaining space, once to allocate it. In a card header that
 * appears on the Home screen and Profile screen, this doubles the measure work
 * for a row that only renders one static frame.</p>
 *
 * <p>The ConstraintLayout replacement resolves all three views in a single measure
 * pass:</p>
 * <ul>
 *   <li>Emoji: constrained to parent start, center-vertical.</li>
 *   <li>Title: {@code layout_width="0dp"} — start constrained to emoji end,
 *       end constrained to chevron start. Fills all remaining space without
 *       triggering a second measure.</li>
 *   <li>Chevron: constrained to parent end, center-vertical.</li>
 * </ul>
 * <p>When the chevron is {@code GONE} (via {@link #setNavigable(false)}),
 * the title's end constraint collapses to the parent end automatically —
 * no layout change required.</p>
 *
 * <h3>Usage in XML</h3>
 * <pre>{@code
 * <!-- Tappable header with chevron (default) -->
 * <com.walkmate.core.designsystem.view.WalkMateCardHeader
 *     android:id="@+id/headerStreak"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:wm_headerEmoji="🔥"
 *     app:wm_headerTitle="Walking Streak"
 *     app:wm_navigable="true" />
 *
 * <!-- Static info header (no chevron) -->
 * <com.walkmate.core.designsystem.view.WalkMateCardHeader
 *     android:id="@+id/headerStats"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:wm_headerEmoji="📊"
 *     app:wm_headerTitle="Your Stats"
 *     app:wm_navigable="false" />
 * }</pre>
 *
 * <h3>Usage at runtime</h3>
 * <pre>{@code
 * // In renderState() — dynamic title
 * headerStreak.setTitle(state.getStreakDays() + " day streak");
 *
 * // Hide chevron when count is zero (no detail screen to navigate to)
 * headerSessions.setNavigable(state.getSessionCount() > 0);
 * }</pre>
 */
public class WalkMateCardHeader extends ConstraintLayout {

    private TextView tvHeaderEmoji;
    private TextView tvHeaderTitle;
    private ImageView ivHeaderChevron;

    // ─── Constructors ────────────────────────────────────────────────────────

    public WalkMateCardHeader(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public WalkMateCardHeader(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public WalkMateCardHeader(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    // ─── Initialisation ──────────────────────────────────────────────────────

    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_walkmate_card_header, this, true);

        tvHeaderEmoji   = findViewById(R.id.tv_header_emoji);
        tvHeaderTitle   = findViewById(R.id.tv_header_title);
        ivHeaderChevron = findViewById(R.id.iv_header_chevron);

        applyAttributes(context, attrs);
    }

    private void applyAttributes(@NonNull Context context, @Nullable AttributeSet attrs) {
        if (attrs == null) return;

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.WalkMateCardHeader);
        try {
            String emoji = a.getString(R.styleable.WalkMateCardHeader_wm_headerEmoji);
            String title = a.getString(R.styleable.WalkMateCardHeader_wm_headerTitle);
            // wm_navigable defaults to true — most headers have a detail action.
            boolean navigable = a.getBoolean(R.styleable.WalkMateCardHeader_wm_navigable, true);

            if (emoji != null) setEmoji(emoji);
            if (title != null) setTitle(title);
            setNavigable(navigable);
        } finally {
            a.recycle();
        }
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Sets the emoji (or short text) at the start of the header.
     * Pass an empty string to hide the emoji space.
     */
    public void setEmoji(@NonNull String emoji) {
        tvHeaderEmoji.setText(emoji);
        tvHeaderEmoji.setVisibility(emoji.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * Sets the header title text.
     */
    public void setTitle(@NonNull String title) {
        tvHeaderTitle.setText(title);
    }

    /**
     * Shows or hides the trailing chevron (navigation indicator).
     *
     * <p>When {@code false}: chevron is {@link View#GONE}, and the title's
     * end-constraint collapses to parent end — no layout code required.</p>
     *
     * <p>When {@code true}: chevron is {@link View#VISIBLE}, and the title
     * automatically ends at the chevron start.</p>
     */
    public void setNavigable(boolean navigable) {
        ivHeaderChevron.setVisibility(navigable ? View.VISIBLE : View.GONE);
    }
}
