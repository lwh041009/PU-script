package com.pu.localapp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG = Color.rgb(245, 245, 245);
    static final int SURFACE = Color.WHITE;
    static final int TEXT = Color.rgb(34, 34, 34);
    static final int MUTED = Color.rgb(138, 138, 138);
    static final int PRIMARY = Color.rgb(255, 122, 26);
    static final int PRIMARY_SOFT = Color.rgb(255, 243, 234);
    static final int SUCCESS = Color.rgb(37, 166, 106);
    static final int WARNING = Color.rgb(245, 142, 36);
    static final int DANGER = Color.rgb(239, 83, 80);
    static final int LINE = Color.rgb(238, 238, 238);
    static final int LINE_STRONG = Color.rgb(226, 226, 226);
    static final int SHADOW = Color.argb(30, 60, 60, 60);

    private Ui() {
    }

    static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    static TextView text(Context context, String value, int sp, int color, int style) {
        TextView tv = new TextView(context);
        tv.setText(value);
        tv.setTextSize(fontSp(context, sp));
        tv.setTextColor(color);
        tv.setTypeface(Typeface.DEFAULT, style);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    static float fontSp(Context context, float sp) {
        return sp * AppSettings.fontScale(context);
    }

    static GradientDrawable bg(int color, float radiusDp, Context context) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(context, (int) radiusDp));
        return d;
    }

    static GradientDrawable strokeBg(int color, int strokeColor, int radiusDp, Context context) {
        GradientDrawable d = bg(color, radiusDp, context);
        d.setStroke(dp(context, 1), strokeColor);
        return d;
    }

    static GradientDrawable strokeBg(int color, int strokeColor, int strokeDp, int radiusDp, Context context) {
        GradientDrawable d = bg(color, radiusDp, context);
        d.setStroke(dp(context, strokeDp), strokeColor);
        return d;
    }

    static Button button(Context context, String text, int bg, int fg) {
        Button b = new Button(context);
        b.setText(text);
        b.setTextSize(fontSp(context, 15));
        b.setTextColor(fg);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(context, 10), 0, dp(context, 10), 0);
        b.setBackground(ripple(strokeBg(bg, bg, 1, 12, context), rippleColor(fg)));
        return b;
    }

    static Button primaryButton(Context context, String text) {
        Button button = button(context, text, PRIMARY, Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(ripple(bg(PRIMARY, 14, context), Color.argb(44, 255, 255, 255)));
        button.setElevation(dp(context, 3));
        return button;
    }

    static Button secondaryButton(Context context, String text) {
        Button button = button(context, text, SURFACE, TEXT);
        button.setBackground(ripple(strokeBg(Color.argb(248, 255, 255, 255), LINE_STRONG, 1, 14, context), Color.argb(26, 255, 122, 26)));
        button.setElevation(dp(context, 1));
        return button;
    }

    static Button softButton(Context context, String text) {
        Button button = button(context, text, PRIMARY_SOFT, PRIMARY);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(ripple(strokeBg(PRIMARY_SOFT, Color.rgb(255, 225, 204), 1, 14, context), Color.argb(32, 255, 122, 26)));
        button.setElevation(dp(context, 1));
        return button;
    }

    static Button toolButton(Context context, String text, boolean active) {
        Button button = button(context, text, active ? PRIMARY_SOFT : Color.argb(248, 255, 255, 255), active ? PRIMARY : TEXT);
        button.setTextSize(fontSp(context, 13));
        button.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        button.setBackground(ripple(
                strokeBg(active ? PRIMARY_SOFT : Color.argb(248, 255, 255, 255), active ? Color.rgb(255, 214, 184) : LINE_STRONG, 1, 999, context),
                Color.argb(30, 255, 122, 26)
        ));
        button.setElevation(dp(context, 1));
        return button;
    }

    static ImageButton iconButton(Context context, int iconRes, boolean active, String description) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(iconRes);
        button.setColorFilter(active ? PRIMARY : TEXT);
        button.setContentDescription(description);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) button.setTooltipText(description);
        button.setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10));
        button.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        button.setBackground(ripple(
                strokeBg(active ? PRIMARY_SOFT : Color.argb(248, 255, 255, 255), active ? Color.rgb(255, 214, 184) : LINE_STRONG, 1, 999, context),
                Color.argb(30, 255, 122, 26)
        ));
        button.setElevation(dp(context, 1));
        return button;
    }

    static RippleDrawable ripple(GradientDrawable content, int color) {
        return new RippleDrawable(ColorStateList.valueOf(color), content, null);
    }

    static RippleDrawable rippleMask(GradientDrawable content, int color, int maskColor) {
        return new RippleDrawable(ColorStateList.valueOf(color), content, new ColorDrawable(maskColor));
    }

    private static int rippleColor(int foreground) {
        return foreground == Color.WHITE ? Color.argb(44, 255, 255, 255) : Color.argb(26, 0, 0, 0);
    }

    static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
        card.setBackground(strokeBg(Color.argb(246, 255, 255, 255), Color.rgb(235, 235, 235), 1, 14, context));
        card.setElevation(dp(context, 2));
        return card;
    }

    static TextView sectionTitle(Context context, String title, String subtitle) {
        TextView text = text(context, subtitle == null || subtitle.isEmpty() ? title : title + "\n" + subtitle, 23, TEXT, Typeface.BOLD);
        text.setLineSpacing(dp(context, 6), 1.0f);
        return text;
    }

    static LinearLayout pageTitle(Context context, String title, String subtitle) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = text(context, title, 23, TEXT, Typeface.BOLD);
        box.addView(titleView);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subtitleView = text(context, subtitle, 12, MUTED, Typeface.NORMAL);
            subtitleView.setSingleLine(true);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.setMargins(0, dp(context, 7), 0, 0);
            box.addView(subtitleView, slp);
        }
        return box;
    }

    static TextView statusPill(Context context, String text, int bg, int fg) {
        TextView pill = text(context, text, 12, fg, Typeface.BOLD);
        pill.setGravity(Gravity.CENTER);
        pill.setSingleLine(true);
        pill.setEllipsize(TextUtils.TruncateAt.END);
        pill.setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5));
        pill.setBackground(bg(bg, 999, context));
        return pill;
    }

    static View emptyState(Context context, String title, String message) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(context, 22), dp(context, 34), dp(context, 22), dp(context, 34));
        TextView titleView = text(context, title, 17, TEXT, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        TextView messageView = text(context, message, 13, MUTED, Typeface.NORMAL);
        messageView.setGravity(Gravity.CENTER);
        messageView.setLineSpacing(dp(context, 4), 1.0f);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.setMargins(0, dp(context, 10), 0, 0);
        box.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(messageView, mlp);
        return box;
    }

    static void updateEmptyState(View state, String title, String message) {
        if (!(state instanceof LinearLayout)) return;
        LinearLayout box = (LinearLayout) state;
        if (box.getChildCount() > 0 && box.getChildAt(0) instanceof TextView) {
            ((TextView) box.getChildAt(0)).setText(title);
        }
        if (box.getChildCount() > 1 && box.getChildAt(1) instanceof TextView) {
            ((TextView) box.getChildAt(1)).setText(message);
        }
    }

    static TextView chip(Context context, String text, int bg, int fg) {
        TextView chip = text(context, text, 13, fg, Typeface.BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5));
        chip.setBackground(bg(bg, 8, context));
        return chip;
    }

    static void applySystemBars(View view) {
        final int left = view.getPaddingLeft();
        final int top = view.getPaddingTop();
        final int right = view.getPaddingRight();
        final int bottom = view.getPaddingBottom();
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(
                    left + insets.getSystemWindowInsetLeft(),
                    top + insets.getSystemWindowInsetTop(),
                    right + insets.getSystemWindowInsetRight(),
                    bottom + insets.getSystemWindowInsetBottom());
            return insets;
        });
        view.requestApplyInsets();
    }

    static View line(Context context) {
        View view = new View(context);
        view.setBackgroundColor(LINE);
        view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)));
        return view;
    }
}
