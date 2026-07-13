package com.pu.localapp;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

final class ReservationScheduler {
    static final String CHANNEL_ID = "reservation";
    static final String EXTRA_RESERVATION_ID = "reservation_id";
    static final String EXTRA_STAGE = "reservation_stage";
    static final String STAGE_PREWARM = "prewarm";
    static final String STAGE_EXECUTE = "execute";
    static final long PREWARM_LEAD_MS = 5000L;
    private final Context context;
    private final AppDb db;

    ReservationScheduler(Context context) {
        this.context = context.getApplicationContext();
        db = new AppDb(this.context);
        ensureChannel();
    }

    long schedule(Models.Account account, Models.Activity activity) {
        Models.Reservation reservation = new Models.Reservation();
        reservation.accountKey = account.key();
        reservation.sid = account.sid;
        reservation.username = account.username;
        reservation.activityId = activity.id;
        reservation.activityName = activity.name;
        reservation.runAt = TimeUtil.parseMillis(activity.joinStartTime);
        reservation.status = "pending";
        reservation.lastResult = "";
        reservation.retryCount = 0;
        reservation.createdAt = BeijingTime.now(context);
        reservation.executor = "local";
        long id = db.upsertReservation(reservation);
        long now = BeijingTime.now(context);
        if (reservation.runAt > now) {
            schedulePrewarm(id, Math.max(now + 100L, reservation.runAt - PREWARM_LEAD_MS));
            scheduleExisting(id, reservation.runAt);
        } else {
            scheduleExisting(id, now + 100L);
        }
        return id;
    }

    void scheduleExisting(long reservationId, long runAt) {
        scheduleAt(reservationId, runAt, STAGE_EXECUTE);
    }

    private void schedulePrewarm(long reservationId, long runAt) {
        scheduleAt(reservationId, runAt, STAGE_PREWARM);
    }

    private void scheduleAt(long reservationId, long runAt, String stage) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = pendingIntent(reservationId, stage);
        if (alarm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
            alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, BeijingTime.elapsedTriggerFor(context, runAt), pi);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, BeijingTime.elapsedTriggerFor(context, runAt), pi);
        } else {
            alarm.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, BeijingTime.elapsedTriggerFor(context, runAt), pi);
        }
    }

    void cancel(long reservationId) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) {
            alarm.cancel(pendingIntent(reservationId, STAGE_PREWARM));
            alarm.cancel(pendingIntent(reservationId, STAGE_EXECUTE));
        }
        db.deleteReservation(reservationId);
    }

    void restorePending() {
        long now = BeijingTime.now(context);
        for (Models.Reservation reservation : db.pendingReservations()) {
            if (reservation.runAt > now) {
                schedulePrewarm(reservation.id, Math.max(now + 100L, reservation.runAt - PREWARM_LEAD_MS));
                scheduleExisting(reservation.id, reservation.runAt);
            } else {
                scheduleExisting(reservation.id, now + 100L);
            }
        }
    }

    boolean canScheduleExact() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return alarm != null && alarm.canScheduleExactAlarms();
    }

    Intent exactAlarmSettingsIntent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null;
        return new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.getPackageName()));
    }

    void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "预约报名", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("预约报名执行结果");
            manager.createNotificationChannel(channel);
        }
    }

    private PendingIntent pendingIntent(long reservationId, String stage) {
        Intent intent = new Intent(context, ReservationReceiver.class);
        intent.setAction("com.pu.localapp.RESERVATION_" + stage);
        intent.putExtra(EXTRA_RESERVATION_ID, reservationId);
        intent.putExtra(EXTRA_STAGE, stage);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, requestCode(reservationId, stage), intent, flags);
    }

    private int requestCode(long reservationId, String stage) {
        int base = (int) (reservationId ^ (reservationId >>> 32));
        return base * 31 + (STAGE_PREWARM.equals(stage) ? 1 : 2);
    }
}
