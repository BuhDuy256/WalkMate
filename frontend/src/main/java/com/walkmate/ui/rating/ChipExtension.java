package com.walkmate.ui.rating;

import android.content.res.ColorStateList;
import com.google.android.material.chip.Chip;
import com.walkmate.data.model.WalkTag;

public class ChipExtension {
    public static void setupFromTag(Chip chip, WalkTag tag) {
        chip.setText(tag.getName());
        chip.setChipIconResource(tag.getIconRes());
        chip.setChipBackgroundColor(ColorStateList.valueOf(tag.getBackgroundColor()));
        chip.setChipStrokeColor(ColorStateList.valueOf(tag.getStrokeColor()));
        chip.setTextColor(tag.getTextColor());
    }
}

