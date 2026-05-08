package com.screenisland.recorder.island;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

public final class IslandTheme {
    public static final int BG = Color.rgb(7, 10, 18);
    public static final int PANEL = Color.rgb(14, 18, 28);
    public static final int PANEL_LIGHT = Color.rgb(30, 39, 58);
    public static final int TEXT = Color.rgb(245, 248, 255);
    public static final int MUTED = Color.rgb(155, 168, 190);
    public static final int ACCENT = Color.rgb(105, 92, 255);
    public static final int RECORD = Color.rgb(255, 71, 87);
    public static final int GO = Color.rgb(0, 224, 132);
    public static final int WARNING = Color.rgb(255, 184, 77);

    private IslandTheme() {
    }

    public static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static GradientDrawable rounded(int color, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    public static GradientDrawable pillGradient(int radiusPx) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(16, 21, 36), Color.rgb(42, 35, 98), Color.rgb(0, 72, 58)}
        );
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }
}
