package com.walkmate.core.designsystem.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.walkmate.R;

public class MatchCardHeaderView extends ConstraintLayout {

    public static final int STYLE_OPEN             = 0;
    public static final int STYLE_MATCHING         = 1;
    public static final int STYLE_PROPOSAL_PENDING = 2;
    public static final int STYLE_PROPOSAL_WAITING = 3;
    public static final int STYLE_SESSION_PENDING  = 4;
    public static final int STYLE_SESSION_ACTIVE   = 5;

    private Chip chipHeaderStatus;
    private CountdownTimerView headerCountdown;

    public MatchCardHeaderView(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public MatchCardHeaderView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public MatchCardHeaderView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_match_card_header, this, true);
        chipHeaderStatus = findViewById(R.id.chipHeaderStatus);
        headerCountdown  = findViewById(R.id.headerCountdown);

        if (attrs == null) return;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MatchCardHeaderView);
        try {
            String label  = a.getString(R.styleable.MatchCardHeaderView_wm_statusLabel);
            int    style  = a.getInt(R.styleable.MatchCardHeaderView_wm_statusStyle, STYLE_OPEN);
            boolean showC = a.getBoolean(R.styleable.MatchCardHeaderView_wm_showCountdown, false);
            if (label != null) setStatus(label, style);
            headerCountdown.setVisibility(showC ? View.VISIBLE : View.GONE);
        } finally {
            a.recycle();
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void setStatus(@NonNull String label, int statusStyle) {
        chipHeaderStatus.setText(label);
        applyStatusStyle(statusStyle);
    }

    public void startCountdown(@NonNull String expiresAtIso) {
        headerCountdown.setVisibility(View.VISIBLE);
        headerCountdown.startCountdown(expiresAtIso);
    }

    public void hideCountdown() {
        headerCountdown.cancelCountdown();
        headerCountdown.setVisibility(View.GONE);
    }

    public void setOnExpiredListener(@Nullable CountdownTimerView.OnExpiredListener listener) {
        headerCountdown.setOnExpiredListener(listener);
    }

    public void cancelCountdown() {
        headerCountdown.cancelCountdown();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void applyStatusStyle(int style) {
        int bgColor;
        int textColor;
        switch (style) {
            case STYLE_MATCHING:
            case STYLE_SESSION_PENDING:
            case STYLE_OPEN:
                bgColor   = ContextCompat.getColor(getContext(), R.color.bg_warm_light);
                textColor = ContextCompat.getColor(getContext(), R.color.orange_end);
                break;
            case STYLE_PROPOSAL_PENDING:
                bgColor   = ContextCompat.getColor(getContext(), R.color.bg_info_light);
                textColor = ContextCompat.getColor(getContext(), R.color.color_info);
                break;
            case STYLE_PROPOSAL_WAITING:
                bgColor   = ContextCompat.getColor(getContext(), R.color.bg_tag_inactive);
                textColor = ContextCompat.getColor(getContext(), R.color.text_label);
                break;
            case STYLE_SESSION_ACTIVE:
                bgColor   = ContextCompat.getColor(getContext(), R.color.bg_success_light);
                textColor = ContextCompat.getColor(getContext(), R.color.color_success);
                break;
            default:
                bgColor   = ContextCompat.getColor(getContext(), R.color.bg_warm_light);
                textColor = ContextCompat.getColor(getContext(), R.color.orange_end);
        }
        chipHeaderStatus.setChipBackgroundColor(ColorStateList.valueOf(bgColor));
        chipHeaderStatus.setTextColor(textColor);
    }
}
