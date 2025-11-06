package com.hrs.firebase.messaging;

import static org.apache.cordova.BuildHelper.getBuildConfigValue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.hrs.firebase.messaging.incomingcall.Constants;
import com.hrs.firebase.messaging.incomingcall.IncomingCall;

import org.apache.cordova.CordovaWebView;
import org.json.JSONException;
import org.json.JSONObject;


import java.util.HashMap;
import java.util.Objects;

import timber.log.Timber;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    public static CordovaWebView webView = null;

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Timber.d("New token: %s", token);
        FCMPlugin.sendTokenRefresh(token);
    }


    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    // [START receive_message]
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
//        // TODO(developer): Handle FCM messages here.
//        // If the application is in the foreground handle both data and notification messages here.
//        // Also if you intend on generating your own notifications as a result of a received FCM
//        // message, here is where that should be initiated. See sendNotification method below.
        Timber.d("==> MyFirebaseMessagingService onMessageReceived");
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


        Boolean isKnoxManage = (Boolean) getBuildConfigValue(getApplicationContext(), "KNOXMANAGE");
        if (FCMPlugin.appInForeground || Build.VERSION.SDK_INT <= Build.VERSION_CODES.S || Boolean.FALSE.equals(isKnoxManage)) {
            handleBannerNotificationForCallLeft(isKnoxManage, data); //Handle native banner dismissal when clinician left call, banner is visible, app is in fg
            FCMPlugin.sendPushPayload(data);
        } else {
            JSONObject jsonData = null;
            try {
                jsonData = new JSONObject((String) Objects.requireNonNull(data.get("jsonData")));
            } catch (JSONException e) {
                Timber.e(e, "Error decoding jsonData from push notification");
            }

            if (jsonData != null && jsonData.optString("action").equals("incoming_call")) {
                try {
                    handleIncomingCall(jsonData, Utils.createNotificationId(jsonData.getString("id")));
                } catch (JSONException e) {
                    Timber.e("Error handling incoming call: %s", e.getMessage());
                }
            } else if (jsonData != null && jsonData.optString("action").equals("call_left")) {
                IncomingCall.callLeft(this);
                FCMPlugin.sendPushPayload(data);
            } else if (jsonData != null && !jsonData.optString("title").isEmpty()) {
                handleGenericNotification(jsonData);
                try {
                    SharedPreferencesManager.getInstance(this).storeNotification(jsonData.getString("id"), jsonData);
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
                FCMPlugin.sendPushPayload(data);
            }
        }

        Timber.d("	Notification Data: %s", data.toString());
    }

    /**
     * Handle call left notification event for IncomingCall banner dismissal
     * @param isKnoxManage
     * @param data
     */
    private void handleBannerNotificationForCallLeft(Boolean isKnoxManage, HashMap<String, Object> data) {
        if(Boolean.TRUE.equals(isKnoxManage)) {
            JSONObject jsonData = null;
            if (data != null && data.get("jsonData") instanceof String) {
                try {
                    jsonData = new JSONObject((String) data.get("jsonData"));
                } catch (JSONException e) {
                    Timber.e(e, "Invalid JSON in push notification data");
                }
            }
            if (jsonData != null && jsonData.optString("action").equals("call_left")) {
                IncomingCall.callLeft(this);
            }
        }
    }

    private void handleGenericNotification(JSONObject jsonData) {
        try {
            new GenericNotification().show(this, jsonData);
        } catch (JSONException e) {
            Timber.e("Failed to generate generic notification  %s", e.getMessage());
        } catch (PackageManager.NameNotFoundException e) {
            Timber.e("Failed to find package name/icon for generic notification  %s", e.getMessage());
        }
    }


    private void handleIncomingCall(JSONObject jsonData, int notificationId) {
        String name = "";
        String title = "";
        if (jsonData.optString("type").equals("video") || jsonData.optString("type").equals("video-zoom")) {
            JSONObject caller = jsonData.optJSONObject("caller");
            if (caller != null) {
                name = caller.optString("name");
            }

            title = "Incoming Video Call";
        } else if (jsonData.optString("type").equals("voice") || jsonData.optString("type").equals("voicecall")) {
            JSONObject callData = jsonData.optJSONObject("data");
            if (callData != null) {
                name = callData.optString("from");
            }
            title = "Incoming Voice Call";
        }

        try {
            IncomingCall.showNotification(this, name, title, Utils.jsonToBundle(jsonData));
        } catch (JSONException e) {
            Timber.e("Failed to show incoming call notification%s", e.getMessage());
        }
    }
}
