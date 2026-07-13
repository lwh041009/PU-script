package com.pu.localapp;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ReservationReceiver extends BroadcastReceiver {
    private static final int PARALLEL_REQUESTS = 4;
    private static final long[] RETRY_DELAYS_MS = {300L, 800L, 1500L};

    @Override
    public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra(ReservationScheduler.EXTRA_RESERVATION_ID, 0);
        if (id == 0) return;
        String stage = intent.getStringExtra(ReservationScheduler.EXTRA_STAGE);
        PendingResult result = goAsync();
        new Thread(() -> {
            try {
                Context app = context.getApplicationContext();
                if (ReservationScheduler.STAGE_PREWARM.equals(stage)) {
                    prewarmReservation(app, id);
                } else {
                    executeReservation(app, id);
                }
            } finally {
                result.finish();
            }
        }).start();
    }

    private void prewarmReservation(Context context, long id) {
        AppDb db = new AppDb(context);
        Models.Reservation reservation = db.getReservation(id);
        if (reservation == null || !"pending".equals(reservation.status)) return;

        Models.Account account = db.getAccountByKey(reservation.accountKey);
        if (account == null) {
            db.updateReservationStatus(id, "failed", "预热失败：账号不存在，请重新登录后再预约", reservation.retryCount);
            notify(context, "预约报名失败", titlePrefix(reservation) + "账号不存在，请重新登录后再预约");
            return;
        }

        PuApi api = new PuApi(context);
        StringBuilder result = new StringBuilder();
        try {
            BeijingTime.syncNow(context);
            appendResult(result, "北京时间已同步");
        } catch (Exception ex) {
            appendResult(result, "北京时间同步失败：" + safeMessage(ex));
        }

        try {
            Models.Account refreshed = api.refreshToken(account);
            account.token = refreshed.token;
            appendResult(result, "token 已刷新");
        } catch (Exception ex) {
            appendResult(result, "token 刷新失败：" + safeMessage(ex));
        }

        try {
            api.getActivityInfo(account, reservation.activityId);
            appendResult(result, "活动连接已预热");
        } catch (Exception ex) {
            appendResult(result, "活动预热失败：" + safeMessage(ex));
        }

        db.updateReservationStatus(id, "pending", "预热结果：" + result, reservation.retryCount);
        new ReservationScheduler(context).scheduleExisting(id, reservation.runAt);
    }

    private void executeReservation(Context context, long id) {
        AppDb db = new AppDb(context);
        Models.Reservation reservation = db.getReservation(id);
        if (reservation == null || !"pending".equals(reservation.status)) return;
        Models.Account account = db.getAccountByKey(reservation.accountKey);
        if (account == null) {
            db.updateReservationStatus(id, "failed", "账号不存在", reservation.retryCount);
            notify(context, "预约报名失败", titlePrefix(reservation) + "账号不存在，请重新登录后再预约");
            return;
        }
        PuApi api = new PuApi(context);
        String prewarmResult = reservation.lastResult == null ? "" : reservation.lastResult.trim();
        db.updateReservationStatus(id, "pending", "正在发送 4 路并发报名请求", 0);

        for (int retry = 0; retry <= RETRY_DELAYS_MS.length; retry++) {
            if (retry > 0) {
                try {
                    Thread.sleep(RETRY_DELAYS_MS[retry - 1]);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    String message = "执行线程被系统中断";
                    db.updateReservationStatus(id, "failed", message, retry - 1);
                    notify(context, "预约报名失败", titlePrefix(reservation) + message);
                    return;
                }
            }

            WaveResult wave = sendConcurrentJoins(api, account, reservation.activityId);
            String roundLabel = "第 " + (retry + 1) + " 轮（4 路并发）";
            if (wave.success) {
                String message = roundLabel + "执行成功：" + wave.message;
                db.updateReservationStatus(id, "completed", message, retry);
                notify(context, "预约报名已执行", titlePrefix(reservation) + message);
                return;
            }
            if (wave.terminal) {
                String message = roundLabel + "停止：" + wave.message;
                db.updateReservationStatus(id, "failed", message, retry);
                notify(context, "预约报名停止", titlePrefix(reservation) + message);
                return;
            }

            if (retry < RETRY_DELAYS_MS.length) {
                db.updateReservationStatus(id, "pending", roundLabel + "失败：" + wave.message +
                        "；将在 " + RETRY_DELAYS_MS[retry] + "ms 后快速重试", retry + 1);
            } else {
                String message = "4 轮并发请求均失败：" + wave.message;
                if (!prewarmResult.isEmpty() && prewarmResult.startsWith("预热结果：")) {
                    message = message + "\n" + prewarmResult;
                }
                db.updateReservationStatus(id, "failed", message, RETRY_DELAYS_MS.length);
                notify(context, "预约报名失败", titlePrefix(reservation) + message);
            }
        }
    }

    private WaveResult sendConcurrentJoins(PuApi api, Models.Account account, long activityId) {
        ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_REQUESTS);
        CompletionService<AttemptResult> completion = new ExecutorCompletionService<>(executor);
        CountDownLatch ready = new CountDownLatch(PARALLEL_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AttemptResult>> futures = new ArrayList<>();

        for (int i = 0; i < PARALLEL_REQUESTS; i++) {
            final int requestNumber = i + 1;
            futures.add(completion.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    return AttemptResult.success(requestNumber, api.join(account, activityId));
                } catch (Exception ex) {
                    return AttemptResult.failure(requestNumber, safeMessage(ex));
                }
            }));
        }

        try {
            ready.await(500L, TimeUnit.MILLISECONDS);
            start.countDown();
            List<String> failures = new ArrayList<>();
            for (int i = 0; i < PARALLEL_REQUESTS; i++) {
                AttemptResult attempt = completion.take().get();
                if (attempt.success) {
                    return WaveResult.success("请求 " + attempt.requestNumber + "：" + attempt.message);
                }
                if (isAlreadyJoined(attempt.message)) {
                    return WaveResult.success("接口反馈已报名：" + attempt.message);
                }
                if (isTerminalBusiness(attempt.message)) {
                    return WaveResult.terminal(attempt.message);
                }
                failures.add("请求 " + attempt.requestNumber + "：" + attempt.message);
            }
            return WaveResult.retryable(joinMessages(failures));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return WaveResult.retryable("并发请求被系统中断");
        } catch (ExecutionException ex) {
            return WaveResult.retryable("并发请求异常：" + safeMessage(ex.getCause()));
        } finally {
            for (Future<AttemptResult> future : futures) future.cancel(true);
            executor.shutdownNow();
        }
    }

    private boolean isAlreadyJoined(String message) {
        return containsAny(message, "已报名", "已经报名", "重复报名", "already joined", "already applied");
    }

    private boolean isTerminalBusiness(String message) {
        return containsAny(message,
                "人数已满", "名额已满", "报名已满", "达到报名上限", "已满员",
                "资格不符", "不符合报名", "无报名资格", "院系限制", "年级限制",
                "报名已结束", "活动已结束", "活动不存在", "活动已删除",
                "full", "not eligible", "not allowed", "ended");
    }

    private boolean containsAny(String value, String... needles) {
        if (value == null) return false;
        String text = value.trim().toLowerCase(java.util.Locale.ROOT);
        for (String needle : needles) {
            if (text.contains(needle.toLowerCase(java.util.Locale.ROOT))) return true;
        }
        return false;
    }

    private String joinMessages(List<String> messages) {
        if (messages.isEmpty()) return "4 个并发请求均未返回具体原因";
        StringBuilder builder = new StringBuilder();
        for (String message : messages) {
            if (builder.length() > 0) builder.append("；");
            builder.append(message);
        }
        String result = builder.toString();
        return result.length() > 900 ? result.substring(0, 900) + "…" : result;
    }

    private void appendResult(StringBuilder builder, String value) {
        if (builder.length() > 0) builder.append("；");
        builder.append(value);
    }

    private String safeMessage(Throwable error) {
        if (error == null) return "未知错误";
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return error.getClass().getSimpleName();
        return message.trim();
    }

    private static class AttemptResult {
        final int requestNumber;
        final boolean success;
        final String message;

        private AttemptResult(int requestNumber, boolean success, String message) {
            this.requestNumber = requestNumber;
            this.success = success;
            this.message = message == null || message.trim().isEmpty() ? "接口未返回具体原因" : message;
        }

        static AttemptResult success(int requestNumber, String message) {
            return new AttemptResult(requestNumber, true, message);
        }

        static AttemptResult failure(int requestNumber, String message) {
            return new AttemptResult(requestNumber, false, message);
        }
    }

    private static class WaveResult {
        final boolean success;
        final boolean terminal;
        final String message;

        private WaveResult(boolean success, boolean terminal, String message) {
            this.success = success;
            this.terminal = terminal;
            this.message = message == null || message.trim().isEmpty() ? "接口未返回具体原因" : message;
        }

        static WaveResult success(String message) {
            return new WaveResult(true, true, message);
        }

        static WaveResult terminal(String message) {
            return new WaveResult(false, true, message);
        }

        static WaveResult retryable(String message) {
            return new WaveResult(false, false, message);
        }
    }

    private String titlePrefix(Models.Reservation reservation) {
        String name = reservation.activityName == null || reservation.activityName.trim().isEmpty()
                ? "活动 #" + reservation.activityId
                : reservation.activityName.trim();
        return name + "\n";
    }

    private void notify(Context context, String title, String text) {
        Intent open = new Intent(context, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(context, 1, open, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, ReservationScheduler.CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setStyle(new Notification.BigTextStyle().bigText(text));
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify((int) BeijingTime.now(context), builder.build());
    }
}
