package com.hrs.firebase.messaging;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

import timber.log.Timber;

public class DeclineCallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getBundleExtra("data");
        HashMap<String, Object> data = new HashMap<>();
        if (bundle != null) {
            data = Utils.bundleToHashMap(bundle);
        }

        MyRingtoneManager.getInstance().stopRingtone();
        NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(IncomingCallNotification.NOTIFICATION_ID);

        Intent cancelIntent = new Intent(IncomingCallNotification.ACTION_CANCEL_DISMISSAL);
        LocalBroadcastManager.getInstance(context).sendBroadcast(cancelIntent);

        try {
            int notificationId = intent.getIntExtra("notificationId", -1);
            if (notificationId != -1) {
                SharedPreferencesManager.getInstance(context).removeNotification(notificationId);
            }
        } catch (JSONException e) {
            Timber.e("Error removing notification from shared preferences: %s", e.getMessage());
        }

        FCMPlugin.sendCallDeclined(data);
    }
}
