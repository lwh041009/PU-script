package com.pu.localapp;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

public class VersionCheckReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult result = goAsync();
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                UpdateChecker.UpdateInfo info = UpdateChecker.checkNow(app);
                if (info != null && UpdateChecker.markNotified(app, info)) notifyUpdate(app, info);
            } catch (Exception ignored) {
                // Background update checks are best effort and should not interrupt the app.
            } finally {
                result.finish();
            }
        }, "pu-background-update-check").start();
    }

    private void notifyUpdate(Context context, UpdateChecker.UpdateInfo info) {
        UpdateScheduler.ensureChannel(context);
        Intent download = new Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(context, info.versionCode, download, flags);
        String detail = info.changelog == null || info.changelog.trim().isEmpty()
                ? "点击通知下载新版本"
                : info.changelog.trim();
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, UpdateScheduler.CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("PU 脚本发现新版本 v" + info.versionName)
                .setContentText("点击查看更新并下载 APK")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setStyle(new Notification.BigTextStyle().bigText(detail));
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(920532 + info.versionCode, builder.build());
    }
}
