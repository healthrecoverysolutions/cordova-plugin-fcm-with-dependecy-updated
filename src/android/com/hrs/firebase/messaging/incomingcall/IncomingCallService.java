package com.hrs.firebase.messaging.incomingcall;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;


import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.hrs.firebase.messaging.FCMPluginActivity;

import java.util.Objects;

import timber.log.Timber;

public class IncomingCallService extends Service {
    public static final String CHANNEL_ID = "incoming_call_channel";
    private static final CharSequence CHANNEL_NAME = "Incoming Calls";
    private static final int NOTIFICATION_ID = 1010;
    private long CALL_TIMEOUT = 90 * 1000;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private MediaPlayer player;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Cancel timeout if still pending
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }

        stopForeground(true);  // remove the notification
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Bundle extras = intent.getExtras();
            if (isAndroidOS16OrMore()) { // For OS >=16, stop ringtone via player
                stopMediaPlayerRingtone();
            }
            switch (action) {
                case Constants.ACTION_ANSWER_CALL:
                    Intent answerIntent = new Intent(Constants.ACTION_ANSWER_CALL);
                    answerIntent.putExtras(Objects.requireNonNull(extras));
                    LocalBroadcastManager.getInstance(this).sendBroadcast(answerIntent);
                    break;
                case Constants.ACTION_DECLINE_CALL:
                    break;
                case Constants.ACTION_CALL_TIMEOUT:
                    Intent callTimeoutIntent = new Intent(Constants.ACTION_CALL_TIMEOUT);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(callTimeoutIntent);
                case Constants.ACTION_CALL_LEFT:
                    Intent callLeftIntent = new Intent(Constants.ACTION_CALL_LEFT);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(callLeftIntent);
                    break;
            }
            stopForeground(true);
            stopSelf();
        } else {
            showIncomingCallNotification(intent);
            if (isAndroidOS16OrMore()) {
                playMediaPlayerRingtone(); // OS 16 is not playing the notification call type sound in a loop thus playing it via media player
            }
            startCallTimeout(intent.getExtras()); // start timeout here for new call
        }

        return START_STICKY;
    }

    private void showIncomingCallNotification(Intent intent) {
        Bundle extras = intent.getExtras();
        PendingIntent fullScreenIntent = getFullScreenIntent(extras);
        PendingIntent answerIntent = getAnswerIntent(intent);
        PendingIntent declineIntent = getDeclineIntent(extras);

        Notification notification;
        String callerName = intent.getStringExtra(Constants.EXTRA_CALLER_NAME);
        String callTitle = intent.getStringExtra(Constants.EXTRA_CALL_TITLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14
            Person caller = new Person.Builder()
                .setName(callerName != null ? callerName : "Unknown Caller")
                .build();

            notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle(callTitle != null ? callTitle : "Incoming Call")
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setFullScreenIntent(fullScreenIntent, true)
                .setStyle(Notification.CallStyle.forIncomingCall(caller, declineIntent, answerIntent))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(answerIntent)
                .build();
        } else {
            ApplicationInfo appInfo = null;
            int largeIcon = 0;
            try {
                appInfo = this.getPackageManager().getApplicationInfo(this.getPackageName(), PackageManager.GET_META_DATA);
                largeIcon = appInfo.metaData.getInt("com.google.firebase.messaging.default_notification_icon", 0);
            } catch (PackageManager.NameNotFoundException e) {
                throw new RuntimeException(e);
            }

            notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(callTitle != null ? callTitle : "Incoming Call")
                .setContentText(callerName != null ? callerName : "Tap to answer")
                .addAction(android.R.drawable.ic_menu_call, "Answer", answerIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declineIntent)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), largeIcon))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenIntent, true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(answerIntent)
                .build();
        }

        startForeground(NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

            channel.setSound(isAndroidOS16OrMore() ? null : ringtoneUri, audioAttributes); // As we are playing this sound via player in a loop for OS 16, we dont set it here
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setImportance(NotificationManager.IMPORTANCE_HIGH);

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private PendingIntent getFullScreenIntent(Bundle extras) {
        Intent fullScreenIntent = new Intent(this, IncomingCallActivity.class);
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        fullScreenIntent.putExtras(extras);

        return PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent getAnswerIntent(Intent intent) {
        Intent answerIntent = new Intent(this, FCMPluginActivity.class);
        answerIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        answerIntent.setAction(Constants.ACTION_ANSWER_CALL);
        Bundle data = intent.getBundleExtra(Constants.EXTRA_CALL_DATA);
        answerIntent.putExtra("data", data);
        return PendingIntent.getActivity(
            this,
            0,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent getDeclineIntent(Bundle extras) {
        Intent declineIntent = new Intent(this, IncomingCallService.class);
        declineIntent.setAction(Constants.ACTION_DECLINE_CALL);
        declineIntent.putExtras(extras);

        return PendingIntent.getService(
            this,
            0,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void startCallTimeout(Bundle extras) {
        timeoutRunnable = () -> {
            Intent timeoutIntent = new Intent(this, IncomingCallService.class);
            timeoutIntent.setAction(Constants.ACTION_CALL_TIMEOUT);
            timeoutIntent.putExtras(extras);
            startService(timeoutIntent);
        };

        timeoutHandler.postDelayed(timeoutRunnable, CALL_TIMEOUT); // 90 seconds
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean isAndroidOS16OrMore() {
       return Build.VERSION.SDK_INT >= 35;
    }

    /**
     * Starts playing the device's default ringtone using MediaPlayer in a loop.
     *
     * <p>Note - On Android OS 16 and above, notification sounds for call-style alerts
     * are not automatically looping when triggered via the notification system.
     * To ensure the ringtone continues playing until the user performs an
     * action on the notification (e.g., accept, dismiss, or open the app),
     * this method manually starts a MediaPlayer instance with looping enabled.
     */
    private void playMediaPlayerRingtone() {
        // Release existing player if already running
        if (player != null) {
            player.release();
            player = null;
        }

        Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        player = MediaPlayer.create(this, ringtoneUri);

        if (player != null) {
            player.setLooping(true);
            player.start();
            Timber.d("Playing media player ringtone in loop");
        }
    }

    /**
     * Stops the looping ringtone playback started by {@link #playMediaPlayerRingtone()}.
     *
     * <p>This method stops and releases the MediaPlayer instance used
     * to manually play the ringtone.
     */
    private void stopMediaPlayerRingtone() {
        if (player != null) {
            try {
                player.stop();
                Timber.d("Stopped media player");
            } catch (IllegalStateException e) {
                Timber.e("Failed to stop media player  %s", e.getMessage());
            }
            player.release();
            player = null;
        }
    }
}
