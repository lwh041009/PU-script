package com.pu.localapp;

import android.content.Context;
import android.content.SharedPreferences;

final class AppSettings {
    static final int MIN_FONT_PERCENT = 50;
    static final int MAX_FONT_PERCENT = 200;
    private static final String PREFS = "pu_app_settings";
    private static final String KEY_FONT_SCALE = "font_scale";
    private static final String KEY_SERVER_BASE_URL = "server_base_url";
    private static final String KEY_SERVER_TOKEN = "server_token";

    private AppSettings() {
    }

    static float fontScale(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return normalizeScale(prefs.getFloat(KEY_FONT_SCALE, 1.0f));
    }

    static void setFontScale(Context context, float scale) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putFloat(KEY_FONT_SCALE, normalizeScale(scale))
                .apply();
    }

    static int fontPercent(Context context) {
        return scaleToPercent(fontScale(context));
    }

    static void setFontPercent(Context context, int percent) {
        setFontScale(context, percentToScale(percent));
    }

    static String serverBaseUrl(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return normalizeServerBaseUrl(prefs.getString(KEY_SERVER_BASE_URL, ""));
    }

    static void setServerBaseUrl(Context context, String url) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SERVER_BASE_URL, normalizeServerBaseUrl(url))
                .apply();
    }

    static String serverToken(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SERVER_TOKEN, "879487").trim();
    }

    static void setServerToken(Context context, String token) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SERVER_TOKEN, token == null ? "" : token.trim())
                .apply();
    }

    static boolean serverEnabled(Context context) {
        return !serverBaseUrl(context).isEmpty();
    }

    static float percentToScale(int percent) {
        return clampPercent(percent) / 100f;
    }

    static int scaleToPercent(float scale) {
        return clampPercent(Math.round(normalizeScale(scale) * 100f));
    }

    static String fontScaleLabel(float scale) {
        int percent = scaleToPercent(scale);
        if (percent < 80) return "很小";
        if (percent < 95) return "小";
        if (percent > 140) return "很大";
        if (percent > 105) return "偏大";
        return "标准";
    }

    static String fontScaleDescription(float scale) {
        int percent = scaleToPercent(scale);
        if (percent < 80) return "极致紧凑，适合只想看更多内容";
        if (percent < 95) return "列表更紧凑，一屏能看到更多内容";
        if (percent > 140) return "文字明显放大，部分内容可能需要更多滚动";
        if (percent > 105) return "文字略大，阅读更轻松";
        return "推荐大小，兼顾清晰和信息密度";
    }

    private static float normalizeScale(float value) {
        return percentToScale(scaleToRawPercent(value));
    }

    private static int scaleToRawPercent(float scale) {
        return Math.round(scale * 100f);
    }

    static String normalizeServerBaseUrl(String value) {
        if (value == null) return "";
        String url = value.trim();
        if (url.isEmpty()) return "";
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    static int clampPercent(int percent) {
        return Math.max(MIN_FONT_PERCENT, Math.min(MAX_FONT_PERCENT, percent));
    }
}
