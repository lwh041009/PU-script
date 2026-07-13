package com.pu.localapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class UpdateChecker {
    private static final String[] MANIFEST_URLS = {
            "https://gitee.com/luo-wanhong/PU-script-release/raw/master/latest.json",
            "https://raw.githubusercontent.com/lwh041009/PU-script/main/update/latest.json",
            "https://cdn.jsdelivr.net/gh/lwh041009/PU-script@main/update/latest.json"
    };
    private static final String PREFS = "pu_update_check";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final String KEY_LAST_NOTIFIED_VERSION = "last_notified_version";
    private static final long AUTO_CHECK_INTERVAL_MS = 60L * 60L * 1000L;

    private UpdateChecker() {
    }

    interface Callback {
        void onSuccess(UpdateInfo info);

        void onError(Exception error);
    }

    static final class UpdateInfo {
        final int versionCode;
        final String versionName;
        final String title;
        final String changelog;
        final String apkUrl;
        final String sha256;
        final int minVersionCode;
        final boolean force;

        UpdateInfo(int versionCode, String versionName, String title, String changelog,
                   String apkUrl, String sha256, int minVersionCode, boolean force) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.title = title;
            this.changelog = changelog;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
            this.minVersionCode = minVersionCode;
            this.force = force;
        }

        boolean isNewer() {
            return versionCode > BuildConfig.VERSION_CODE;
        }

        boolean requiresUpdate() {
            return force || (minVersionCode > 0 && BuildConfig.VERSION_CODE < minVersionCode);
        }
    }

    static void check(Context context, boolean manual, Callback callback) {
        Context app = context.getApplicationContext();
        long now = System.currentTimeMillis();
        long lastCheck = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_CHECK, 0L);
        if (!manual && now - lastCheck < AUTO_CHECK_INTERVAL_MS) {
            postSuccess(callback, null);
            return;
        }

        new Thread(() -> {
            try {
                postSuccess(callback, checkNow(app));
            } catch (Exception ex) {
                postError(callback, ex);
            }
        }, "pu-update-check").start();
    }

    static UpdateInfo checkNow(Context context) throws Exception {
        Context app = context.getApplicationContext();
        UpdateInfo info = fetch();
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .apply();
        return info != null && info.isNewer() ? info : null;
    }

    static boolean markNotified(Context context, UpdateInfo info) {
        if (info == null) return false;
        Context app = context.getApplicationContext();
        android.content.SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int last = prefs.getInt(KEY_LAST_NOTIFIED_VERSION, 0);
        if (info.versionCode <= last) return false;
        prefs.edit().putInt(KEY_LAST_NOTIFIED_VERSION, info.versionCode).apply();
        return true;
    }

    private static UpdateInfo fetch() throws Exception {
        Exception last = null;
        for (String manifestUrl : MANIFEST_URLS) {
            try {
                return fetchFrom(manifestUrl);
            } catch (Exception ex) {
                last = ex;
            }
        }
        throw last == null ? new IllegalStateException("无法获取版本信息") : last;
    }

    private static UpdateInfo fetchFrom(String manifestUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(manifestUrl).openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(8000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "PU-Script-Android-Update");
        int code = connection.getResponseCode();
        try {
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("版本服务 HTTP " + code);
            }
            String text = readAll(connection.getInputStream());
            JSONObject json = new JSONObject(text);
            int versionCode = json.optInt("versionCode", 0);
            String versionName = json.optString("versionName", "").trim();
            String apkUrl = json.optString("apkUrl", "").trim();
            if (versionCode <= 0 || versionName.isEmpty() || apkUrl.isEmpty()) {
                throw new IllegalStateException("版本信息不完整");
            }
            return new UpdateInfo(
                    versionCode,
                    versionName,
                    json.optString("title", "PU 脚本").trim(),
                    readChangelog(json.opt("changelog")),
                    apkUrl,
                    json.optString("sha256", "").trim(),
                    json.optInt("minVersionCode", 0),
                    json.optBoolean("force", false)
            );
        } finally {
            connection.disconnect();
        }
    }

    private static String readChangelog(Object value) {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                String item = array.optString(i, "").trim();
                if (item.isEmpty()) continue;
                if (builder.length() > 0) builder.append("\n");
                builder.append("• ").append(item);
            }
            return builder.toString();
        }
        return value == null || value == JSONObject.NULL ? "" : String.valueOf(value).trim();
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }

    private static void postSuccess(Callback callback, UpdateInfo info) {
        if (callback == null) return;
        new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(info));
    }

    private static void postError(Callback callback, Exception error) {
        if (callback == null) return;
        new Handler(Looper.getMainLooper()).post(() -> callback.onError(error));
    }
}
