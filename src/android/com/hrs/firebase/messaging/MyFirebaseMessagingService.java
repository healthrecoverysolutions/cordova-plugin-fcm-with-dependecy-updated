package com.hrs.firebase.messaging;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;


import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import timber.log.Timber;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

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

        JSONObject jsonData = null;
        try {
            jsonData = new JSONObject((String) Objects.requireNonNull(data.get("jsonData")));
        } catch (JSONException e) {
            Timber.e(e, "Error decoding jsonData from push notification");
        }

        if (jsonData != null && jsonData.optString("action").equals("incoming_call")) {
            String name = "";
            if (jsonData.optString("type").equals("video")) {
                JSONObject caller = jsonData.optJSONObject("caller");
                if (caller != null) {
                    caller.optString("name");
                }
            }

            if (jsonData.optString("type").equals("voice") || jsonData.optString("type").equals("voicecall")) {
                JSONObject callData = jsonData.optJSONObject("data");
                if (callData != null) {
                    name = callData.optString("from");
                }
            }

            try {
                new IncomingCallNotification().show(this, jsonData, name);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        } else {
            FCMPlugin.sendPushPayload(data);
        }

        Timber.d("	Notification Data: %s", data.toString());
    }
    // [END receive_message]
}
