package com.walkmate.data.model;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;

import java.util.Objects;

public class WalkTag {
    private final String id;
    private final String name;
    @DrawableRes
    private final int iconRes;
    @ColorInt
    private final int backgroundColor;
    @ColorInt
    private final int strokeColor;
    @ColorInt
    private final int textColor;

    public WalkTag(String id, String name, int iconRes, int backgroundColor, int strokeColor, int textColor) {
        this.id = id;
        this.name = name;
        this.iconRes = iconRes;
        this.backgroundColor = backgroundColor;
        this.strokeColor = strokeColor;
        this.textColor = textColor;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getIconRes() {
        return iconRes;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public int getStrokeColor() {
        return strokeColor;
    }

    public int getTextColor() {
        return textColor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WalkTag walkTag = (WalkTag) o;
        return iconRes == walkTag.iconRes && backgroundColor == walkTag.backgroundColor && strokeColor == walkTag.strokeColor && textColor == walkTag.textColor && Objects.equals(id, walkTag.id) && Objects.equals(name, walkTag.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, iconRes, backgroundColor, strokeColor, textColor);
    }

    @Override
    public String toString() {
        return "WalkTag{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", iconRes=" + iconRes +
                ", backgroundColor=" + backgroundColor +
                ", strokeColor=" + strokeColor +
                ", textColor=" + textColor +
                '}';
    }
}

