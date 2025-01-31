package com.hrs.firebase.messaging;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.media.RingtoneManager;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class IncomingCallNotification {
    private static final String CHANNEL_ID = "incoming_call_notification_channel";
    private static final int NOTIFICATION_ID = 1001;

    public void show(Context context, JSONObject data, String callerName) throws JSONException {
        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        createNotificationChannel(notificationManager);
        PendingIntent answerPendingIntent = getAnswerIntent(context, data);
        PendingIntent declinePendingIntent = getDeclineIntent(context, data);
        PendingIntent fullScreenPendingIntent = getFullScreenIntent(context);



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Person incomingCaller = new Person.Builder()
                .setName(callerName)
                .setImportant(true)
                .build();

            Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setContentTitle(data.getString("title"))
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setStyle(
                    Notification.CallStyle.forIncomingCall(
                        incomingCaller,
                        declinePendingIntent,
                        answerPendingIntent
                    )
                )
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setOngoing(true);

            notificationManager.notify(NOTIFICATION_ID, builder.build());
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            MyRingtoneManager.getInstance().playRingtone(context, soundUri, 1000);
            scheduleDismissal(notificationManager);
        } else {
            // Fallback for older versions (show a basic notification or skip)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                    .setContentTitle("Incoming Call")
                    .setContentText(callerName)
                    .setSmallIcon(android.R.drawable.sym_call_incoming)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setFullScreenIntent(fullScreenPendingIntent, true);
                notificationManager.notify(NOTIFICATION_ID, builder.build());
            }
        }
    }
    private void createNotificationChannel(NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID);

            if (existingChannel != null) {
                notificationManager.deleteNotificationChannel(CHANNEL_ID);
            }

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build();

            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Incoming Video Calls",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for incoming calls");
            channel.setSound(null, audioAttributes);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private PendingIntent getDeclineIntent(Context context, JSONObject data) throws JSONException {
        Intent declineIntent = new Intent(context, DeclineCallReceiver.class);
        Bundle bundle = Utils.jsonToBundle(data);
        declineIntent.putExtra("data", bundle);
        declineIntent.putExtra("notificationId", NOTIFICATION_ID);
        return PendingIntent.getBroadcast(
            context,
            1,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent getAnswerIntent(Context context, JSONObject data) throws JSONException {
        Intent answerIntent = new Intent(context, AnswerCallReceiver.class);
        Bundle bundle = Utils.jsonToBundle(data);
        answerIntent.putExtra("data", bundle);
        answerIntent.putExtra("notificationId", NOTIFICATION_ID);
        return PendingIntent.getBroadcast(
            context,
            0,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent getFullScreenIntent(Context context) {
        Intent fullScreenIntent = new Intent(context, IncomingCallActivity.class);
        return PendingIntent.getActivity(
            context,
            2,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void scheduleDismissal(NotificationManager notificationManager) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            notificationManager.cancel(NOTIFICATION_ID);
            MyRingtoneManager.getInstance().stopRingtone();
            scheduler.shutdown();
        }, 10, TimeUnit.SECONDS);
    }
}

