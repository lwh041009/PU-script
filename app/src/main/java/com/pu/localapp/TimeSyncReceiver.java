package com.pu.localapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TimeSyncReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult result = goAsync();
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                BeijingTime.syncNow(app);
            } catch (Exception ignored) {
            } finally {
                new ReservationScheduler(app).restorePending();
                BeijingTime.scheduleNextSync(app);
                result.finish();
            }
        }).start();
    }
}
