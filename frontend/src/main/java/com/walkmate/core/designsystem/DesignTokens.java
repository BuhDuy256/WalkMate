package com.walkmate.core.designsystem;

/**
 * DesignTokens.java
 *
 * Converted from: src/styles/theme.css + src/guidelines/
 * Place at: core/designsystem/DesignTokens.java
 *
 * Single source of truth for design tokens in Java code.
 * Use these constants in code; use @color/, @dimen/, @style/ in XML.
 *
 * Architecture note (from frontend-architecture.md):
 * core/designsystem/ is the Android equivalent of src/guidelines/ + src/styles/
 */
public final class DesignTokens {

    private DesignTokens() {
    }

    // ── Font weights (from --font-weight-medium / --font-weight-normal) ──
    public static final int FONT_WEIGHT_NORMAL = 400;
    public static final int FONT_WEIGHT_MEDIUM = 500;

    // ── Border radius in dp (from --radius-* tokens) ──
    public static final int RADIUS_SM = 6; // calc(0.625rem - 4px)
    public static final int RADIUS_MD = 8; // calc(0.625rem - 2px)
    public static final int RADIUS_LG = 10; // 0.625rem
    public static final int RADIUS_XL = 14; // calc(0.625rem + 4px)

    // ── Typography scale in sp (from Tailwind default scale) ──
    public static final int TEXT_SM = 14;
    public static final int TEXT_BASE = 16;
    public static final int TEXT_LG = 18;
    public static final int TEXT_XL = 20;
    public static final int TEXT_2XL = 24;

    // ── Spacing scale in dp (Tailwind 4-unit system) ──
    public static final int SPACING_1 = 4;
    public static final int SPACING_2 = 8;
    public static final int SPACING_3 = 12;
    public static final int SPACING_4 = 16;
    public static final int SPACING_5 = 20;
    public static final int SPACING_6 = 24;
    public static final int SPACING_8 = 32;
    public static final int SPACING_10 = 40;
    public static final int SPACING_12 = 48;
    public static final int SPACING_16 = 64;
}
