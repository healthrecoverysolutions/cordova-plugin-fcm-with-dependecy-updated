package com.hrs.firebase.messaging;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.apache.cordova.CordovaWebView;
import org.json.JSONException;
import org.json.JSONObject;


import java.util.HashMap;
import java.util.Objects;

import timber.log.Timber;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private long TIME_TO_FULLY_BOOT = (60 * 1000) + (40 * 1000); //2 * 60 * 1000; somewhere 1 min 40 sec
    @Override
    public void onCreate() {
        super.onCreate();
//        Handler handler = new Handler(Looper.getMainLooper());
//        Runnable runnable = new Runnable() {
//            @Override
//            public void run() {
//                Log.e("AB", "***************** LOCK TASK check ");
//                ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
//                int state = 0;
//
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                    state = activityManager.getLockTaskModeState();
//                    String label = "";
//                    switch (state) {
//                        case 0:
//                            label = "LOCK_TASK_MODE_NONE";
//                            break;
//                        case 1:
//                            label = "LOCK_TASK_MODE_LOCKED";
//                            break;
//                        case 2:
//                            label = "LOCK_TASK_MODE_PINNED";
//                            break;
//                        default:
//                            label = "UNKNOWN";
//                            break;
//                    }
//                    Log.d("MyFirebaseMessageService", "LOCK TASK LEVEL " + label);
//                }
//                handler.postDelayed(this, 1000); // 1 second interval
//            }
//            // }, 1000); // 20 seconds in milliseconds
//            // );
//        };
//// Start the loop
//        handler.post(runnable);

    }

    public static CordovaWebView webView = null;
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Timber.d("New token: %s", token);
        FCMPlugin.sendTokenRefresh(token);
    }

    private static boolean DEVICE_JUST_BOOTED = false;

//    @Override
//    public void onMessageReceived(RemoteMessage remoteMessage) {
//
//            NotificationHelper.showNotification(this, remoteMessage);
//
//    }
    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    // [START receive_message]
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.e("AB", "%%%%%%%%%%% HANDLING NotfiicaTIoNS ######################");
        if (remoteMessage.getNotification() != null) {
            Log.e("AB", "	Notification Title: " + remoteMessage.getNotification().getTitle());
            Log.e("AB", "	Notification Message: " + remoteMessage.getNotification().getBody());
        }
        if(isCallLeft(remoteMessage)) {
            Log.e("AB", "-->> got a CALL LEFT notification ----->>");
            cancelIncomingCallNotificationIntentAlarm(); // cancel an existing alarm which would have notified this call which was just left
        }

        ActivityManager activityManager = (ActivityManager) getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
        int lockTaskModeState = activityManager.getLockTaskModeState();
        Log.e("AB", " HANDLING lockTaskModeState " + lockTaskModeState);
        long uptimeMillis = SystemClock.elapsedRealtime(); // Returns milliseconds since boot, including time spent in sleep.
        long bootTimestamp = System.currentTimeMillis() - uptimeMillis;
        long twoMinutesAfterBoot = bootTimestamp + (TIME_TO_FULLY_BOOT); // 2 minutes after boot
        // long currentTime = System.currentTimeMillis();
        // long delay = twoMinutesAfterBoot - currentTime;

        if ((uptimeMillis > TIME_TO_FULLY_BOOT)) { // more than 2 minutes .... TODO remove ! as its just for testing
            Log.d("BootCheck", "Device has been up for a while " + uptimeMillis);
            DEVICE_JUST_BOOTED = false;

        } else {
            Log.d("BootCheck", "Device was just booted " + uptimeMillis);
            DEVICE_JUST_BOOTED = true;
        }
        if (!DEVICE_JUST_BOOTED/*lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE*//* lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED*/) {
            Log.e("AB", "$$$$$$$$$$$$$$$$$ HANDLING NOTIFICATION RIGHT AWAY ----->> ");
            // Show the notification immediately
            NotificationHelper.showNotification(this, remoteMessage);
        } else {
            // Postpone the notification by 20 seconds... this handler gets destroyed as the app itself gets destroyed.. we need to do something with shared prefs of something like that or may be listen to the state

            Log.e("AB", "***************** HANDLING AFTER 20 SECONDS ");


            int uniqueRequestCode = (int) System.currentTimeMillis(); // unique per notification to schedule them all
            Intent intent = new Intent(getApplicationContext(), PendingNotificationReceiver.class);
            intent.putExtra("message", remoteMessage);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                getApplicationContext(),
                uniqueRequestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Store the intent code for incoming_call notification in order to cancel this if it was left at a later point
            if(isIncomingCall(remoteMessage)) {
                Log.e("AB", "-->> got a INCOMING CALL notification ----->>" );
                saveIntentIdForIncomingCall(uniqueRequestCode);
            }

            AlarmManager alarmManager = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
            // long triggerTime = System.currentTimeMillis() + 30 * 1000; // 30 seconds
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                twoMinutesAfterBoot,
                pendingIntent
            );
        }

    }

    private boolean isIncomingCall(RemoteMessage remoteMessage) {
        boolean isIncomingCall = false;
        JSONObject jsonData = getJSONDataForNotification(remoteMessage);
        if (jsonData != null && jsonData.optString("action").equals("incoming_call")) {
            // Save requestCode using a unique key, like a user ID or message type
            isIncomingCall = true;
        }
        return  isIncomingCall;
    }

    private JSONObject getJSONDataForNotification(RemoteMessage remoteMessage) {
        boolean isIncomingCall = false;
        HashMap<String, Object> data = new HashMap<String, Object>();
        data.put("wasTapped", false);

        if(remoteMessage.getNotification() != null){
            data.put("title", remoteMessage.getNotification().getTitle());
            data.put("body", remoteMessage.getNotification().getBody());
        }

        for (String key : remoteMessage.getData().keySet()) {
            Object value = remoteMessage.getData().get(key);
            data.put(key, value);
        }
        JSONObject jsonData = null;
        try {

            jsonData = new JSONObject((String) Objects.requireNonNull(data.get("jsonData")));
            Log.e("NotificationHelper", "==> json data " + jsonData.toString());
        } catch (JSONException e) {
            Timber.e(e, "Error decoding jsonData from push notification");
        }
        return jsonData;
    }

    private boolean isCallLeft(RemoteMessage remoteMessage) {
        boolean isCallLeft = false;
        JSONObject jsonData = getJSONDataForNotification(remoteMessage);
        if (jsonData != null && jsonData.optString("action").equals("call_left")) {
            // Save requestCode using a unique key, like a user ID or message type
            isCallLeft = true;
        }
        return  isCallLeft;
    }

    private void saveIntentIdForIncomingCall(int uniqueRequestCode) {
// Save requestCode using a unique key, like a user ID or message type
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        prefs.edit().putInt("alarm_call_incoming", uniqueRequestCode).apply();
    }

    private void cancelIncomingCallNotificationIntentAlarm(){
        // Check whether we got a call_left notification, if yes, cancel incoming_call pending intent scheduled earlier
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        int requestCode = prefs.getInt("alarm_call_incoming", -1);
            Intent intent = new Intent(getApplicationContext(), PendingNotificationReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            getApplicationContext(),
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);
        Log.e("AB", "-->> CANCELLED OLD NOTFICIATION ----->>");
    }
    }

//    public void showNotification(RemoteMessage remoteMessage){
//        //        // TODO(developer): Handle FCM messages here.
////        // If the application is in the foreground handle both data and notification messages here.
////        // Also if you intend on generating your own notifications as a result of a received FCM
////        // message, here is where that should be initiated. See sendNotification method below.
//        Timber.d("==> MyFirebaseMessagingService onMessageReceived");
////
//        if(remoteMessage.getNotification() != null){
//            Timber.d("	Notification Title: %s", remoteMessage.getNotification().getTitle());
//            Timber.d("	Notification Message: %s", remoteMessage.getNotification().getBody());
//        }
//
//        HashMap<String, Object> data = new HashMap<String, Object>();
//        data.put("wasTapped", false);
//
//        if(remoteMessage.getNotification() != null){
//            data.put("title", remoteMessage.getNotification().getTitle());
//            data.put("body", remoteMessage.getNotification().getBody());
//        }
//
//        for (String key : remoteMessage.getData().keySet()) {
//            Object value = remoteMessage.getData().get(key);
//            Timber.d("\tKey: " + key + " Value: " + value);
//            data.put(key, value);
//        }
//
//        Boolean isKnoxManage = (Boolean) getBuildConfigValue(getApplicationContext(), "KNOXMANAGE");
//        if (FCMPlugin.appInForeground || Build.VERSION.SDK_INT <= Build.VERSION_CODES.S || Boolean.FALSE.equals(isKnoxManage)) {
//            FCMPlugin.sendPushPayload(data);
//        } else {
//            JSONObject jsonData = null;
//            try {
//                jsonData = new JSONObject((String) Objects.requireNonNull(data.get("jsonData")));
//            } catch (JSONException e) {
//                Timber.e(e, "Error decoding jsonData from push notification");
//            }
//
//            if (jsonData != null && jsonData.optString("action").equals("incoming_call")) {
//                try {
//                    handleIncomingCall(jsonData, Utils.createNotificationId(jsonData.getString("id")));
//                } catch (JSONException e) {
//                    Timber.e("Error handling incoming call: %s", e.getMessage());
//                }
//            } else if (jsonData != null && jsonData.optString("action").equals("call_left")) {
//                broadcastCallLeft(this);
//                FCMPlugin.sendPushPayload(data);
//            } else if (jsonData != null && !jsonData.optString("title").isEmpty()) {
//                handleGenericNotification(jsonData);
//                try {
//                    SharedPreferencesManager.getInstance(this).storeNotification(jsonData.getString("id"), jsonData);
//                } catch (JSONException e) {
//                    Timber.e("Error getting storing notification in shared preferences: %s", e.getMessage());
//                }
//
//                if (jsonData.optString("status").equals("deactivate")) {
//                    Timber.d("Incoming deactivate patient notification. Deleting token.");
//                    try {
//                        FirebaseMessaging.getInstance().deleteToken();
//                    } catch (Exception e) {
//                        Timber.e("Patient deactivated. Error deleting Firebase instance: %s", e.getMessage());
//                    }
//                }
//            } else {
//                FCMPlugin.sendPushPayload(data);
//            }
//        }
//
//        Timber.d("	Notification Data: %s", data.toString());
//    }
//
//    private void broadcastCallLeft(Context context) {
//        Intent intent = new Intent("CALL_LEFT");
//        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
//    }
//
//    private void handleGenericNotification(JSONObject jsonData) {
//        try {
//            new GenericNotification().show(this, jsonData);
//        } catch (JSONException e) {
//            Timber.e("Failed to generate generic notification  %s", e.getMessage());
//        } catch (PackageManager.NameNotFoundException e) {
//            Timber.e("Failed to find package name/icon for generic notification  %s", e.getMessage());
//        }
//    }
//
//
//    private void handleIncomingCall(JSONObject jsonData, int notificationId) {
//        String name = "";
//        if (jsonData.optString("type").equals("video") || jsonData.optString("type").equals("video-zoom")) {
//            JSONObject caller = jsonData.optJSONObject("caller");
//            if (caller != null) {
//                name = caller.optString("name");
//            }
//        } else if (jsonData.optString("type").equals("voice") || jsonData.optString("type").equals("voicecall")) {
//            JSONObject callData = jsonData.optJSONObject("data");
//            if (callData != null) {
//                name = callData.optString("from");
//            }
//        }
//
//        try {
//            new IncomingCallNotification(this, notificationId).show(jsonData, name);
//        } catch (JSONException e) {
//            Timber.e("Failed to generate incoming call notification  %s", e.getMessage());
//        }
//    }

