package com.hrs.firebase.messaging;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

import timber.log.Timber;

public class GenericNotification {
    private static final String CHANNEL_ID = "generic_channel_id";

    public void show(Context context, JSONObject data) throws JSONException, PackageManager.NameNotFoundException {
        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        createNotificationChannel(notificationManager);

        int notificationId = Utils.createNotificationId((String) data.get("id"));
        PendingIntent pendingIntent = getPendingIntent(context, data, notificationId);
        ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
        int defaultIcon = appInfo.metaData.getInt("com.google.firebase.messaging.default_notification_icon", 0);

        Notification.Builder builder = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID)
                .setContentTitle(data.getString("title"))
                .setContentText(data.getString("body"))
                .setSmallIcon(defaultIcon)
                .setContentIntent(pendingIntent);
        }

        notificationManager.notify(notificationId, builder != null ? builder.build() : null);
    }

    private void createNotificationChannel(NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Generic Notifications",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for incoming calls");
            notificationManager.createNotificationChannel(channel);
        }
    }

    private PendingIntent getPendingIntent(Context context, JSONObject data, int notificationId) throws JSONException {
        Intent intent = new Intent(context, FCMPluginActivity.class);
        Bundle bundle = Utils.jsonToBundle(data);
        intent.putExtra("data", bundle);
        intent.putExtra("notificationId", notificationId);
        return PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        );
    }
}

