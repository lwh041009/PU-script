package com.pu.localapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class BeijingTime {
    static final long SYNC_INTERVAL_MS = 30L * 60L * 1000L;
    static final TimeZone ZONE = TimeZone.getTimeZone("Asia/Shanghai");
    private static final String PREF = "beijing_time";
    private static final String KEY_OFFSET = "offset_ms";
    private static final String KEY_SYNCED_AT = "synced_at";
    private static final String ACTION_SYNC = "com.pu.localapp.TIME_SYNC";
    private static final Object LOCK = new Object();
    private static volatile boolean loaded;
    private static volatile boolean syncing;
    private static volatile long offsetMs;
    private static volatile long syncedAt;

    private BeijingTime() {
    }

    static long now(Context context) {
        syncIfNeeded(context);
        return System.currentTimeMillis() + offset(context);
    }

    static long offset(Context context) {
        load(context);
        return offsetMs;
    }

    static long lastSyncedAt(Context context) {
        load(context);
        return syncedAt;
    }

    static void syncIfNeeded(Context context) {
        if (context == null) return;
        load(context);
        long now = System.currentTimeMillis();
        if (syncedAt > 0 && now - syncedAt < SYNC_INTERVAL_MS) return;
        synchronized (LOCK) {
            if (syncing) return;
            syncing = true;
        }
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                syncNow(app);
            } catch (Exception ignored) {
            } finally {
                syncing = false;
                scheduleNextSync(app);
            }
        }).start();
    }

    static void syncNow(Context context) throws Exception {
        syncFromNetwork(context, "HEAD");
    }

    static void updateFromHttpDate(Context context, String dateHeader, long requestStart, long responseAt) {
        if (context == null || dateHeader == null || dateHeader.trim().isEmpty()) return;
        try {
            long serverMillis = parseHttpDate(dateHeader);
            if (serverMillis <= 0) return;
            long localMidpoint = (requestStart + responseAt) / 2L;
            saveOffset(context.getApplicationContext(), serverMillis - localMidpoint, responseAt);
        } catch (Exception ignored) {
        }
    }

    static long delayUntil(Context context, long beijingTargetMillis) {
        long delay = beijingTargetMillis - now(context);
        return Math.max(delay, 1000L);
    }

    static long elapsedTriggerFor(Context context, long beijingTargetMillis) {
        return SystemClock.elapsedRealtime() + delayUntil(context, beijingTargetMillis);
    }

    static void scheduleNextSync(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        AlarmManager alarm = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        long trigger = SystemClock.elapsedRealtime() + SYNC_INTERVAL_MS;
        PendingIntent pi = syncPendingIntent(app);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
        } else {
            alarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
        }
    }

    private static void syncFromNetwork(Context context, String method) throws Exception {
        long start = System.currentTimeMillis();
        HttpURLConnection conn = (HttpURLConnection) new URL("https://apis.pocketuni.net/uc/school/list").openConnection();
        try {
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod(method);
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("Origin", "https://class.pocketuni.net");
            conn.setRequestProperty("Referer", "https://class.pocketuni.net/");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.getResponseCode();
            long end = System.currentTimeMillis();
            String date = conn.getHeaderField("Date");
            if ((date == null || date.isEmpty()) && "HEAD".equals(method)) {
                syncFromNetwork(context, "GET");
                return;
            }
            updateFromHttpDate(context, date, start, end);
        } finally {
            conn.disconnect();
        }
    }

    private static long parseHttpDate(String value) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date date = format.parse(value);
        return date == null ? 0 : date.getTime();
    }

    private static void load(Context context) {
        if (loaded || context == null) return;
        synchronized (LOCK) {
            if (loaded) return;
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
            offsetMs = prefs.getLong(KEY_OFFSET, 0L);
            syncedAt = prefs.getLong(KEY_SYNCED_AT, 0L);
            loaded = true;
        }
    }

    private static void saveOffset(Context context, long offset, long syncTime) {
        synchronized (LOCK) {
            offsetMs = offset;
            syncedAt = syncTime;
            loaded = true;
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_OFFSET, offset)
                    .putLong(KEY_SYNCED_AT, syncTime)
                    .apply();
        }
    }

    private static PendingIntent syncPendingIntent(Context context) {
        Intent intent = new Intent(context, TimeSyncReceiver.class);
        intent.setAction(ACTION_SYNC);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, 920531, intent, flags);
    }
}
