package com.walkmate.core.designsystem.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.walkmate.R;
import com.walkmate.domain.walkpost.PostVisibility;

public class VisibilitySelectorView extends LinearLayout {

    public interface OnVisibilitySelectedListener {
        void onVisibilitySelected(PostVisibility visibility);
    }

    private View optionPublic;
    private View optionFriends;
    private View optionPrivate;
    private PostVisibility selected = PostVisibility.PUBLIC;
    private OnVisibilitySelectedListener listener;

    public VisibilitySelectorView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public VisibilitySelectorView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public VisibilitySelectorView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_visibility_selector, this, true);

        optionPublic  = findViewById(R.id.option_public);
        optionFriends = findViewById(R.id.option_friends);
        optionPrivate = findViewById(R.id.option_private);

        optionPublic.setOnClickListener(v -> select(PostVisibility.PUBLIC));
        optionFriends.setOnClickListener(v -> select(PostVisibility.FRIENDS));
        optionPrivate.setOnClickListener(v -> select(PostVisibility.PRIVATE));

        updateSelectionUI();
    }

    public void setSelectedVisibility(PostVisibility visibility) {
        this.selected = visibility != null ? visibility : PostVisibility.PUBLIC;
        updateSelectionUI();
    }

    public PostVisibility getSelectedVisibility() {
        return selected;
    }

    public void setOnVisibilitySelectedListener(OnVisibilitySelectedListener listener) {
        this.listener = listener;
    }

    private void select(PostVisibility visibility) {
        this.selected = visibility;
        updateSelectionUI();
        if (listener != null) listener.onVisibilitySelected(visibility);
    }

    private void updateSelectionUI() {
        optionPublic.setBackgroundResource(selected == PostVisibility.PUBLIC
                ? R.drawable.bg_visibility_option_selected
                : R.drawable.bg_visibility_option_unselected);
        optionFriends.setBackgroundResource(selected == PostVisibility.FRIENDS
                ? R.drawable.bg_visibility_option_selected
                : R.drawable.bg_visibility_option_unselected);
        optionPrivate.setBackgroundResource(selected == PostVisibility.PRIVATE
                ? R.drawable.bg_visibility_option_selected
                : R.drawable.bg_visibility_option_unselected);
    }
}
