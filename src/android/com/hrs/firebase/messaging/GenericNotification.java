package com.hrs.firebase.messaging;

import static android.content.Context.POWER_SERVICE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.system.Os;

import org.json.JSONException;
import org.json.JSONObject;

import ionic.hrsmobile.byod.patient.R;


public class GenericNotification {
    private static final String CHANNEL_ID = "generic_channel_id";

    public void show(Context context, JSONObject data) throws JSONException, PackageManager.NameNotFoundException {
        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        createNotificationChannel(notificationManager);

        int notificationId = Utils.createNotificationId(data.getString("id"));
        PendingIntent pendingIntent = getPendingIntent(context, data, notificationId);
        ApplicationInfo appInfo = context.getPackageManager()
            .getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
        int largeIcon = appInfo.metaData.getInt("com.google.firebase.messaging.default_notification_icon", 0);
        String title = data.optString("title", "");
        String body = data.optString("body", "");

        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID);

        builder
            .setSmallIcon(R.drawable.ic_notification_tray)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), largeIcon));

        notificationManager.notify(notificationId, builder.build());
        wakeUp(context);
    }

    private void createNotificationChannel(NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Generic Notifications",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for incoming calls");
            channel.enableLights(true);
            Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            channel.setSound(defaultSoundUri, Notification.AUDIO_ATTRIBUTES_DEFAULT);
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

    /**
     * Wakeup the device.
     *
     * @param context The application context.
     */
    private void wakeUp(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(POWER_SERVICE);

        if (pm == null)
            return;

        int level =   PowerManager.SCREEN_DIM_WAKE_LOCK
            | PowerManager.ACQUIRE_CAUSES_WAKEUP;

        PowerManager.WakeLock wakeLock = pm.newWakeLock(level, "com.hrs.patient:FirebaseMessage");

        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(1000);

        wakeLock.release();
    }
}

