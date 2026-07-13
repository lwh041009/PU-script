package com.pu.localapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            BeijingTime.syncIfNeeded(context);
            BeijingTime.scheduleNextSync(context);
            UpdateScheduler.schedule(context);
            new ReservationScheduler(context).restorePending();
        }
    }
}
