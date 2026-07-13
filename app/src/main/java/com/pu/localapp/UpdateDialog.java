package com.pu.localapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

final class UpdateDialog {
    private UpdateDialog() {
    }

    static void show(Activity activity, UpdateChecker.UpdateInfo info) {
        StringBuilder message = new StringBuilder();
        message.append("当前版本：").append(BuildConfig.VERSION_NAME)
                .append("\n最新版本：").append(info.versionName);
        if (info.changelog != null && !info.changelog.trim().isEmpty()) {
            message.append("\n\n更新内容：\n").append(info.changelog.trim());
        }
        if (info.sha256 != null && !info.sha256.isEmpty()) {
            message.append("\n\n下载后请保持 APK 来源为官方发布地址。");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(info.title == null || info.title.isEmpty() ? "发现新版本" : info.title + "有新版本")
                .setMessage(message.toString())
                .setPositiveButton("下载更新", (dialog, which) -> openDownload(activity, info.apkUrl));
        if (!info.requiresUpdate()) builder.setNegativeButton("稍后", null);
        AlertDialog dialog = builder.create();
        dialog.setCancelable(!info.requiresUpdate());
        dialog.show();
    }

    private static void openDownload(Activity activity, String apkUrl) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
            activity.startActivity(intent);
        } catch (Exception ex) {
            Toast.makeText(activity, "没有可用的浏览器打开下载地址", Toast.LENGTH_LONG).show();
        }
    }
}
