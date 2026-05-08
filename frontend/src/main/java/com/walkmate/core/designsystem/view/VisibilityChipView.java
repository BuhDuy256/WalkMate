package com.walkmate.core.designsystem.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.walkmate.R;
import com.walkmate.domain.walkpost.PostVisibility;

public class VisibilityChipView extends FrameLayout {

    private TextView txtChip;

    public VisibilityChipView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public VisibilityChipView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public VisibilityChipView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_visibility_chip, this, true);
        txtChip = findViewById(R.id.txt_visibility_chip);
    }

    public void setVisibility(PostVisibility visibility) {
        if (visibility == null) visibility = PostVisibility.PRIVATE;
        txtChip.setText(visibility.toDisplayLabel());
        switch (visibility) {
            case PUBLIC:
                txtChip.setBackgroundResource(R.drawable.bg_visibility_chip_public);
                txtChip.setTextColor(0xFF166534);
                break;
            case FRIENDS:
                txtChip.setBackgroundResource(R.drawable.bg_visibility_chip_friends);
                txtChip.setTextColor(0xFF1D4ED8);
                break;
            case PRIVATE:
            default:
                txtChip.setBackgroundResource(R.drawable.bg_visibility_chip_private);
                txtChip.setTextColor(0xFF374151);
                break;
        }
    }
}
