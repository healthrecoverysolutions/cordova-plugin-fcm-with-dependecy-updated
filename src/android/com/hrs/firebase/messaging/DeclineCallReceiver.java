package com.hrs.firebase.messaging;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.util.HashMap;

public class DeclineCallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getBundleExtra("data");
        HashMap<String, Object> data = new HashMap<>();
        if (bundle != null) {
            data = Utils.bundleToHashMap(bundle);
        }

        MyRingtoneManager.getInstance().stopRingtone();
        int notificationId = intent.getIntExtra("notificationId", -1);
        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null && notificationId != -1) {
            notificationManager.cancel(notificationId);
        }

        FCMPlugin.sendCallDeclined(data);
    }
}
