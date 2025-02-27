package com.hrs.firebase.messaging;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.media.RingtoneManager;
import android.os.Bundle;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;


public class IncomingCallNotification {
    private static final String CHANNEL_ID = "incoming_call_notification_channel";
    public static final int NOTIFICATION_ID = 1001;
    public static final String ACTION_CANCEL_DISMISSAL = "CANCEL_DISMISSAL";
    private final Context context;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledFuture;
    private final NotificationManager notificationManager;

    public IncomingCallNotification(Context context) {
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.context = context;
        LocalBroadcastManager.getInstance(context).registerReceiver(cancelReceiver, new IntentFilter(ACTION_CANCEL_DISMISSAL));
        IntentFilter callLeftFilter = new IntentFilter("CALL_LEFT");
        LocalBroadcastManager.getInstance(context).registerReceiver(finishReceiver, callLeftFilter);
    }

    private final BroadcastReceiver finishReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            dismissNotification();
            cancelScheduledDismissal();
        }
    };

    private final BroadcastReceiver cancelReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Timber.d("#BroadcastReceiver: Received cancel request.");
            cancelScheduledDismissal();
        }
    };


    public void show(JSONObject data, String callerName) throws JSONException {
        createNotificationChannel();

        String title = data.getString("title");
        PendingIntent answerPendingIntent = getAnswerIntent(context, data);
        PendingIntent declinePendingIntent = getDeclineIntent(context, data);
        PendingIntent fullScreenPendingIntent = getFullScreenIntent(context, data, callerName, title);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Person incomingCaller = new Person.Builder()
                .setName(callerName)
                .setImportant(true)
                .build();

            Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setStyle(
                    Notification.CallStyle.forIncomingCall(
                        incomingCaller,
                        declinePendingIntent,
                        answerPendingIntent
                    )
                )
                .setContentIntent(answerPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true);

            this.notificationManager.notify(NOTIFICATION_ID, builder.build());
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            MyRingtoneManager.getInstance().playRingtone(context, soundUri, 1000);
            scheduleDismissal();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel existingChannel = this.notificationManager.getNotificationChannel(CHANNEL_ID);

            if (existingChannel != null) {
                this.notificationManager.deleteNotificationChannel(CHANNEL_ID);
            }

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build();

            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for incoming calls");
            channel.setSound(null, audioAttributes);
            this.notificationManager.createNotificationChannel(channel);
        }
    }

    private PendingIntent getDeclineIntent(Context context, JSONObject data) throws JSONException {
        Intent declineIntent = new Intent(context, DeclineCallReceiver.class);
        Bundle bundle = Utils.jsonToBundle(data);
        declineIntent.putExtra("data", bundle);
        declineIntent.putExtra("notificationId", Utils.createNotificationId(data.getString("id")));
        return PendingIntent.getBroadcast(
            context,
            1,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent getAnswerIntent(Context context, JSONObject data) throws JSONException {
        Intent answerIntent = new Intent(context, FCMPluginActivity.class);
        Bundle bundle = Utils.jsonToBundle(data);
        answerIntent.putExtra("data", bundle);
        answerIntent.putExtra("notificationId", Utils.createNotificationId(data.getString("id")));
        answerIntent.setAction("ANSWER_CALL");
        return PendingIntent.getActivity(
            context,
            0,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent getFullScreenIntent(Context context, JSONObject data, String caller, String title) throws JSONException {
        Intent fullScreenIntent = new Intent(context, IncomingCallActivity.class);
        Bundle bundle = Utils.jsonToBundle(data);
        fullScreenIntent.putExtra("data", bundle);
        fullScreenIntent.putExtra("notificationId", Utils.createNotificationId(data.getString("id")));
        fullScreenIntent.putExtra("caller", caller);
        fullScreenIntent.putExtra("title", title);
        return PendingIntent.getActivity(
            context,
            2,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void scheduleDismissal() {
        Timber.d("#scheduleDismissal(): Scheduling incoming call dismissal");
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow(); // Cancel any existing scheduler
        }

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduledFuture = scheduler.schedule(this::dismissNotification, 90, TimeUnit.SECONDS);
    }

    public void dismissNotification() {
        Timber.d("#scheduleDismissal(): Incoming call dismissal triggered");
        this.notificationManager.cancel(NOTIFICATION_ID);
        MyRingtoneManager.getInstance().stopRingtone();
        Intent intent = new Intent("FINISH_INCOMING_CALL_ACTIVITY");
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        scheduler.shutdown();

        try {
            int notificationId = intent.getIntExtra("id", -1);
            if (notificationId != -1) {
                SharedPreferencesManager.getInstance(context).removeNotification(notificationId);
            }
        } catch (JSONException e) {
            Timber.e("Error removing notification from shared preferences: %s", e.getMessage());
        }

        this.unregisterReceiver();
    }

    private void cancelScheduledDismissal() {
        Timber.d("#cancelScheduledDismissal(): Cancelling scheduled incoming call notification dismissal");
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(true);
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }

        this.unregisterReceiver();
    }

    public void unregisterReceiver() {
        Timber.d("#unregisterReceiver(): unregister CANCEL_DISMISSAL incoming call receiver");
        LocalBroadcastManager.getInstance(context).unregisterReceiver(cancelReceiver);
        LocalBroadcastManager.getInstance(context).unregisterReceiver(finishReceiver);
    }
}

