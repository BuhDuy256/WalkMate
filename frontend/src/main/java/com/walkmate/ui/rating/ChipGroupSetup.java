package com.walkmate.ui.rating;

import android.content.Context;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.walkmate.data.model.WalkTag;
import com.walkmate.data.model.WalkTagData;
import java.util.List;

public class ChipGroupSetup {
    public interface OnTagSelectedListener {
        void onTagSelected(WalkTag tag);
    }

    public static void setupChipGroup(
            ChipGroup chipGroup,
            Context context) {
        setupChipGroup(chipGroup, context, WalkTagData.getAllTags(), null);
    }

    public static void setupChipGroup(
            ChipGroup chipGroup,
            Context context,
            OnTagSelectedListener onTagSelected) {
        setupChipGroup(chipGroup, context, WalkTagData.getAllTags(), onTagSelected);
    }

    public static void setupChipGroup(
            ChipGroup chipGroup,
            Context context,
            List<WalkTag> tags,
            OnTagSelectedListener onTagSelected) {
        chipGroup.removeAllViews();

        for (WalkTag tag : tags) {
            Chip chip = new Chip(context);
            ChipExtension.setupFromTag(chip, tag);
            chip.setCheckable(true);

            OnTagSelectedListener finalOnTagSelected = onTagSelected;
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && finalOnTagSelected != null) {
                    finalOnTagSelected.onTagSelected(tag);
                }
            });

            chipGroup.addView(chip);
        }
    }
}

