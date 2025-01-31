package com.hrs.firebase.messaging;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.util.HashMap;

import ionic.hrsmobile.byod.patient.dev2.MainActivity;

public class AnswerCallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getBundleExtra("data");
        HashMap<String, Object> data = new HashMap<>();
        if (bundle != null) {
            data = Utils.bundleToHashMap(bundle);
        }

        // makes pcm answer the call
        data.put("wasTapped", true);

        MyRingtoneManager.getInstance().stopRingtone();
        int notificationId = intent.getIntExtra("notificationId", -1);
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null && notificationId != -1) {
            notificationManager.cancel(notificationId);
        }

        Intent activityIntent = new Intent(context, MainActivity.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(activityIntent);

        FCMPlugin.sendPushPayload(data);
    }
}
