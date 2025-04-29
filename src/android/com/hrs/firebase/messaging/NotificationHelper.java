package com.hrs.firebase.messaging;

import static org.apache.cordova.BuildHelper.getBuildConfigValue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Objects;

import timber.log.Timber;

public class NotificationHelper {
    public static void showNotification(Context context, RemoteMessage remoteMessage){
        //        // TODO(developer): Handle FCM messages here.
//        // If the application is in the foreground handle both data and notification messages here.
//        // Also if you intend on generating your own notifications as a result of a received FCM
//        // message, here is where that should be initiated. See sendNotification method below.
        Timber.d("==> NotificationHelper showNotification");
        Log.e("NotificationHelper", "==> NotificationHelper showNotification ");
//
        if(remoteMessage.getNotification() != null){
            Timber.d("	Notification Title: %s", remoteMessage.getNotification().getTitle());
            Timber.d("	Notification Message: %s", remoteMessage.getNotification().getBody());
        }

        HashMap<String, Object> data = new HashMap<String, Object>();
        data.put("wasTapped", false);

        if(remoteMessage.getNotification() != null){
            data.put("title", remoteMessage.getNotification().getTitle());
            data.put("body", remoteMessage.getNotification().getBody());
        }

        for (String key : remoteMessage.getData().keySet()) {
            Object value = remoteMessage.getData().get(key);
            Timber.d("\tKey: " + key + " Value: " + value);
            data.put(key, value);
        }

        Boolean isKnoxManage = (Boolean) getBuildConfigValue(context, "KNOXMANAGE");
        if (FCMPlugin.appInForeground || Build.VERSION.SDK_INT <= Build.VERSION_CODES.S || Boolean.FALSE.equals(isKnoxManage)) {
            FCMPlugin.sendPushPayload(data);
            Log.e("NotificationHelper", "==> SEND PUSHPAYLOAD ");
        } else {
            JSONObject jsonData = null;
            try {

                jsonData = new JSONObject((String) Objects.requireNonNull(data.get("jsonData")));
                Log.e("NotificationHelper", "==> json data " + jsonData.toString());
            } catch (JSONException e) {
                Timber.e(e, "Error decoding jsonData from push notification");
            }

            if (jsonData != null && jsonData.optString("action").equals("incoming_call")) {
                try {
                    Log.e("NotificationHelper", "Handle incoming call ---");
                    handleIncomingCall(context, jsonData, Utils.createNotificationId(jsonData.getString("id")));
                    Log.e("NotificationHelper", "Handled incoming call ---");
                } catch (JSONException e) {
                    Timber.e("Error handling incoming call: %s", e.getMessage());
                }
            } else if (jsonData != null && jsonData.optString("action").equals("call_left")) {
                Log.e("NotificationHelper", "call left ---");
                broadcastCallLeft(context);
                FCMPlugin.sendPushPayload(data);
            } else if (jsonData != null && !jsonData.optString("title").isEmpty()) {
                Log.e("NotificationHelper", "Handle generic notification ---");
                handleGenericNotification(context, jsonData);
                try {
                    SharedPreferencesManager.getInstance(context).storeNotification(jsonData.getString("id"), jsonData);
                } catch (JSONException e) {
                    Timber.e("Error getting storing notification in shared preferences: %s", e.getMessage());
                }

                if (jsonData.optString("status").equals("deactivate")) {
                    Timber.d("Incoming deactivate patient notification. Deleting token.");
                    try {
                        FirebaseMessaging.getInstance().deleteToken();
                    } catch (Exception e) {
                        Timber.e("Patient deactivated. Error deleting Firebase instance: %s", e.getMessage());
                    }
                }
            } else {
                Log.e("NotificationHelper", "ELSE send push payload");
                FCMPlugin.sendPushPayload(data);
            }
        }

        Timber.d("	Notification Data: %s", data.toString());
        Log.e("NotificationHelper", "	Notification Data:" + data.toString());
    }

    private static void broadcastCallLeft(Context context) {
        Intent intent = new Intent("CALL_LEFT");
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private static void handleGenericNotification(Context context, JSONObject jsonData) {
        try {
            new GenericNotification().show(context, jsonData);
        } catch (JSONException e) {
            Timber.e("Failed to generate generic notification  %s", e.getMessage());
        } catch (PackageManager.NameNotFoundException e) {
            Timber.e("Failed to find package name/icon for generic notification  %s", e.getMessage());
        }
    }


    private static void handleIncomingCall(Context context, JSONObject jsonData, int notificationId) {
        String name = "";
        if (jsonData.optString("type").equals("video") || jsonData.optString("type").equals("video-zoom")) {
            JSONObject caller = jsonData.optJSONObject("caller");
            if (caller != null) {
                name = caller.optString("name");
            }
        } else if (jsonData.optString("type").equals("voice") || jsonData.optString("type").equals("voicecall")) {
            JSONObject callData = jsonData.optJSONObject("data");
            if (callData != null) {
                name = callData.optString("from");
            }
        }

        try {
            new IncomingCallNotification(context, notificationId).show(jsonData, name);
        } catch (JSONException e) {
            Timber.e("Failed to generate incoming call notification  %s", e.getMessage());
        }
    }
}
