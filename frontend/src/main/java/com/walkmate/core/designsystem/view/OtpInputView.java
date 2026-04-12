package com.walkmate.core.designsystem.view;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.walkmate.R;

/**
 * 6-digit OTP input: six single-character boxes with auto-focus-advance.
 *
 * Usage:
 *   String code = otpInputView.getOtp();  // returns "" if incomplete, 6-char string if full
 *   otpInputView.setEnabled(false);       // disables all boxes during loading
 *   otpInputView.clear();                 // clears all boxes and focuses the first
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
        setGravity(Gravity.CENTER);

        int boxSize = dpToPx(context, 44);
        int gap     = dpToPx(context, 8);

        for (int i = 0; i < OTP_LENGTH; i++) {
            EditText box = new EditText(context);
            box.setId(generateViewId());
            box.setInputType(InputType.TYPE_CLASS_NUMBER);
            box.setTextAlignment(TEXT_ALIGNMENT_CENTER);
            box.setGravity(Gravity.CENTER);
            box.setTextSize(20);
            box.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1)});
            box.setBackground(context.getDrawable(R.drawable.bg_otp_box));

            LayoutParams lp = new LayoutParams(boxSize, boxSize);
            if (i < OTP_LENGTH - 1) lp.setMarginEnd(gap);
            box.setLayoutParams(lp);

            final int idx = i;
            box.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void afterTextChanged(Editable s) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() == 1 && idx < OTP_LENGTH - 1) {
                        boxes[idx + 1].requestFocus();
                    }
                }
            });

            box.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && keyCode == KeyEvent.KEYCODE_DEL
                        && box.getText().length() == 0
                        && idx > 0) {
                    boxes[idx - 1].requestFocus();
                    boxes[idx - 1].setText("");
                    return true;
                }
                return false;
            });

            boxes[i] = box;
            addView(box);
        }
    }

    /** @return 6-character digit string, or empty string if any box is unfilled. */
    public String getOtp() {
        StringBuilder sb = new StringBuilder();
        for (EditText box : boxes) {
            String text = box.getText().toString();
            if (text.isEmpty()) return "";
            sb.append(text);
        }
        return sb.toString();
    }

    /** Clears all boxes and focuses the first one. */
    public void clear() {
        for (EditText box : boxes) box.setText("");
        boxes[0].requestFocus();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (EditText box : boxes) box.setEnabled(enabled);
    }

    private int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
