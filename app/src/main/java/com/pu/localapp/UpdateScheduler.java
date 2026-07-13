package com.pu.localapp;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

final class UpdateScheduler {
    static final String CHANNEL_ID = "updates";
    private static final String ACTION = "com.pu.localapp.VERSION_CHECK";
    private static final int REQUEST_CODE = 920532;
    private static final long INTERVAL_MS = 60L * 60L * 1000L;

    private UpdateScheduler() {
    }

    static void schedule(Context context) {
        Context app = context.getApplicationContext();
        ensureChannel(app);
        AlarmManager alarm = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        PendingIntent pendingIntent = pendingIntent(app);
        alarm.cancel(pendingIntent);
        long firstTrigger = SystemClock.elapsedRealtime() + INTERVAL_MS;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, firstTrigger, INTERVAL_MS, pendingIntent);
        } else {
            alarm.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, firstTrigger, INTERVAL_MS, pendingIntent);
        }
    }

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "版本更新", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("PU 脚本新版本提醒");
        manager.createNotificationChannel(channel);
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, VersionCheckReceiver.class);
        intent.setAction(ACTION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
    }
}
