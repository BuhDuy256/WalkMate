package com.walkmate.core.designsystem.view;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.walkmate.R;

/**
 * 6-digit OTP input: auto-focus-advance on digit entry, backspace-to-prev navigation.
 * Fully programmatic — no layout file. API: getOtp(), clear(), setEnabled().
 */
public class OtpInputView extends LinearLayout {

    private static final int OTP_LENGTH = 6;

    private final EditText[] boxes = new EditText[OTP_LENGTH];

    public OtpInputView(Context context) {
        super(context);
        init(context);
    }

    public OtpInputView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public OtpInputView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);

        int boxSizePx  = dpToPx(context, 44);
        int marginPx   = dpToPx(context, 6);

        for (int i = 0; i < OTP_LENGTH; i++) {
            EditText box = buildBox(context, boxSizePx);

            LayoutParams lp = new LayoutParams(boxSizePx, boxSizePx);
            if (i > 0) lp.leftMargin = marginPx;
            addView(box, lp);
            boxes[i] = box;
        }

        for (int i = 0; i < OTP_LENGTH; i++) {
            wireListeners(i);
        }
    }

    private EditText buildBox(Context context, int sizePx) {
        EditText et = new EditText(context);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(1) });
        et.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        et.setTextSize(20f);
        et.setTextColor(ContextCompat.getColor(context, R.color.text_dark));
        et.setPadding(0, 0, 0, 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(context, 8));
        bg.setStroke(dpToPx(context, 1), ContextCompat.getColor(context, R.color.bg_tag_inactive));
        bg.setColor(ContextCompat.getColor(context, R.color.white));
        et.setBackground(bg);

        return et;
    }

    private void wireListeners(int index) {
        EditText box = boxes[index];

        box.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (s.length() == 1 && index < OTP_LENGTH - 1) {
                    boxes[index + 1].requestFocus();
                }
            }
        });

        box.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && keyCode == KeyEvent.KEYCODE_DEL
                    && box.getText().length() == 0
                    && index > 0) {
                boxes[index - 1].requestFocus();
                boxes[index - 1].setText("");
                return true;
            }
            return false;
        });
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns the 6-digit OTP string, or empty string if any box is unfilled. */
    public String getOtp() {
        StringBuilder sb = new StringBuilder();
        for (EditText box : boxes) {
            String digit = box.getText().toString();
            if (digit.isEmpty()) return "";
            sb.append(digit);
        }
        return sb.toString();
    }

    /** Clears all boxes and moves focus to the first. */
    public void clear() {
        for (EditText box : boxes) box.setText("");
        boxes[0].requestFocus();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (EditText box : boxes) box.setEnabled(enabled);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int dpToPx(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
