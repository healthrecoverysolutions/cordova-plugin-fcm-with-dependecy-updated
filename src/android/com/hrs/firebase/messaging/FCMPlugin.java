package com.hrs.firebase.messaging;

import androidx.core.app.NotificationManagerCompat;

import android.app.NotificationManager;
import android.content.Context;
import android.util.Pair;

import com.hrs.firebase.messaging.interfaces.*;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;

import timber.log.Timber;

public class FCMPlugin extends CordovaPlugin {
    private static final String ACTION_READY = "ready";
    private static final String ACTION_SET_SHARED_EVENT_DELEGATE = "setSharedEventDelegate";
    private static final String ACTION_GET_TOKEN = "getToken";
    private static final String ACTION_GET_INITIAL_PUSH_PAYLOAD = "getInitialPushPayload";
    private static final String ACTION_SUBSCRIBE_TO_TOPIC = "subscribeToTopic";
    private static final String ACTION_UNSUBSCRIBE_FROM_TOPIC = "unsubscribeFromTopic";
    private static final String ACTION_INIT_DIFFERENT_ACCOUNT = "initDifferentAccount";
    private static final String ACTION_CLEAR_ALL_NOTIFICATIONS = "clearAllNotifications";
    private static final String ACTION_CREATE_NOTIFICATION_CHANNEL = "createNotificationChannel";
    private static final String ACTION_DELETE_INSTANCE_ID = "deleteInstanceId";
    private static final String ACTION_HAS_PERMISSION = "hasPermission";

    private static final String EVENT_TYPE_NOTIFICATION = "notification";
    private static final String EVENT_TYPE_TOKEN_REFRESH = "tokenRefresh";

    private static FCMPlugin instance = null;
    private static final LinkedList<Pair<String, String>> bufferedEvents = new LinkedList<>();
    private static Map<String, Object> initialPushPayload = null;
    private CallbackContext sharedEventDelegate = null;

    @Override
    public void pluginInitialize() {
        super.pluginInitialize();
        instance = this;
        Timber.d("==> FCMPlugin initialize");
        FirebaseMessaging.getInstance().subscribeToTopic("android");
        FirebaseMessaging.getInstance().subscribeToTopic("all");
    }

    @Override
    public void onDestroy() {
        Timber.i("onDestroy()");
        instance = null;
        initialPushPayload = null;
    }

    public boolean execute(
        final String action,
        final JSONArray args,
        final CallbackContext callbackContext
    ) throws JSONException {
        Timber.d("==> FCMPlugin execute: %s", action);
        try {
            switch (action) {
                case ACTION_READY:
                    callbackContext.success();
                    break;
                case ACTION_SET_SHARED_EVENT_DELEGATE:
                    Timber.i("overridding event bridge");
                    sharedEventDelegate = callbackContext;
                    break;
                case ACTION_GET_TOKEN:
                    cordova.getActivity().runOnUiThread(() -> getToken(callbackContext));
                    break;
                case ACTION_GET_INITIAL_PUSH_PAYLOAD:
                    cordova.getActivity().runOnUiThread(() -> getInitialPushPayload(callbackContext));
                    break;
                case ACTION_SUBSCRIBE_TO_TOPIC:
                    cordova.getThreadPool().execute(() -> {
                        try {
                            FirebaseMessaging.getInstance().subscribeToTopic(args.getString(0));
                            callbackContext.success();
                        } catch (Exception e) {
                            callbackContext.error(e.getMessage());
                        }
                    });
                    break;
                case ACTION_UNSUBSCRIBE_FROM_TOPIC:
                    cordova.getThreadPool().execute(() -> {
                        try {
                            final String topic = args.getString(0);
                            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic);
                            callbackContext.success();
                        } catch (Exception e) {
                            callbackContext.error(e.getMessage());
                        }
                    });
                    break;
                case ACTION_INIT_DIFFERENT_ACCOUNT:
                    cordova.getThreadPool().execute(() -> {
                        try {
                            Context context = cordova.getActivity();
                            if (!FirebaseApp.getApps(context).isEmpty()) {
                                FirebaseApp app = FirebaseApp.getInstance("[DEFAULT]");
                                app.delete();
                            }
                            JSONObject accountInfo = args.getJSONObject(0);
                            FirebaseOptions options = new FirebaseOptions.Builder()
                                .setProjectId(accountInfo.getString("project_id"))
                                .setApplicationId(accountInfo.getString("app_id"))
                                .setApiKey(accountInfo.getString("api_key"))
                                .build();
                            FirebaseApp.initializeApp(context, options);
                            callbackContext.success();
                        } catch (Exception e) {
                            callbackContext.error(e.getMessage());
                        }
                    });
                    break;
                case ACTION_CLEAR_ALL_NOTIFICATIONS:
                    cordova.getThreadPool().execute(() -> {
                        try {
                            Context context = cordova.getActivity();
                            NotificationManager nm = (NotificationManager) context
                                .getSystemService(Context.NOTIFICATION_SERVICE);
                            nm.cancelAll();
                            callbackContext.success();
                        } catch (Exception e) {
                            callbackContext.error(e.getMessage());
                        }
                    });
                    break;
                case ACTION_CREATE_NOTIFICATION_CHANNEL:
                    cordova.getActivity().runOnUiThread(() -> {
                        Context context = cordova.getContext();
                        FCMPluginChannelCreator creator = new FCMPluginChannelCreator(context);
                        creator.createNotificationChannel(callbackContext, args);
                    });
                    break;
                case ACTION_DELETE_INSTANCE_ID:
                    deleteInstanceId(callbackContext);
                    break;
                case ACTION_HAS_PERMISSION:
                    hasPermission(callbackContext);
                    break;
                default:
                    callbackContext.error("Method not found");
                    return false;
            }
        } catch (Exception e) {
            Timber.e(e, "ERROR: onPluginAction: %s", e.getMessage());
            callbackContext.error(e.getMessage());
            return false;
        }

        return true;
    }

    public void getInitialPushPayload(CallbackContext callback) {
        if (initialPushPayload == null) {
            Timber.d("getInitialPushPayload: null");
            callback.success((String) null);
            return;
        }
        Timber.d("getInitialPushPayload");
        try {
            JSONObject jo = new JSONObject();
            for (String key : initialPushPayload.keySet()) {
                jo.put(key, initialPushPayload.get(key));
                Timber.d("\tinitialPushPayload: " + key + " => " + initialPushPayload.get(key));
            }
            callback.success(jo);
        } catch (Exception error) {
            try {
                callback.error(exceptionToJson(error));
            } catch (JSONException jsonErr) {
                Timber.e(jsonErr, "Error when parsing json");
            }
        }
    }

    public void getToken(final TokenListeners<String, JSONObject> callback) {
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Timber.e(task.getException(), "getInstanceId failed");
                    try {
                        callback.error(exceptionToJson(Objects.requireNonNull(task.getException())));
                    } catch (JSONException jsonErr) {
                        Timber.e(jsonErr, "Error when parsing json");
                    }
                    return;
                }

                // Get new Instance ID token
                String newToken = task.getResult();

                Timber.i("\tToken: %s", newToken);
                callback.success(newToken);
            });

            FirebaseMessaging.getInstance().getToken().addOnFailureListener(e -> {
                try {
                    Timber.e(e, "Error retrieving token: ");
                    callback.error(exceptionToJson(e));
                } catch (JSONException jsonErr) {
                    Timber.e(jsonErr, "Error when parsing json");
                }
            });
        } catch (Exception e) {
            Timber.e(e, "\tError retrieving token");
            try {
                callback.error(exceptionToJson(e));
            } catch (JSONException ignored) {
            }
        }
    }

    private void deleteInstanceId(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebaseMessaging.getInstance().deleteToken();
                    callbackContext.success();
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void hasPermission(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            try {
                NotificationManagerCompat notificationManagerCompat =
                    NotificationManagerCompat.from(cordova.getActivity().getApplicationContext());
                callbackContext.success(notificationManagerCompat.areNotificationsEnabled() ? 1 : 0);
            } catch (Exception e) {
                callbackContext.error(e.getMessage());
            }
        });
    }

    private JSONObject exceptionToJson(final Exception exception) throws JSONException {
        return new JSONObject() {
            {
                put("message", exception.getMessage());
                put("cause", exception.getClass().getName());
                put("stacktrace", Arrays.toString(exception.getStackTrace()));
            }
        };
    }

    public void getToken(final CallbackContext callbackContext) {
        this.getToken(new TokenListeners<String, JSONObject>() {
            @Override
            public void success(String message) {
                callbackContext.success(message);
            }

            @Override
            public void error(JSONObject message) {
                callbackContext.error(message);
            }
        });
    }

    private static boolean isWaitingForValidPluginInstance() {
        return instance == null || instance.sharedEventDelegate == null;
    }

    private static void dispatchJSEvent(String eventName, String stringifiedJSONValue) throws JSONException {
        if (isWaitingForValidPluginInstance()) {
            Timber.d("\tUnable to send event due to unreachable bridge context");
            return;
        }

        JSONArray payload = new JSONArray();
        payload.put(0, eventName);
        payload.put(1, stringifiedJSONValue);

        PluginResult dataResult = new PluginResult(PluginResult.Status.OK, payload);
        dataResult.setKeepCallback(true);

        FCMPlugin.instance.sharedEventDelegate.sendPluginResult(dataResult);
        Timber.d("\tSent event: " + eventName + " with " + stringifiedJSONValue);
    }

    private static void bufferJSEvent(String eventName, String stringifiedJSONValue) throws JSONException {
        if (isWaitingForValidPluginInstance()) {
            bufferedEvents.add(new Pair<>(eventName, stringifiedJSONValue));
            // prevent this buffer from growing infinitely
            while (bufferedEvents.size() > 50) {
                bufferedEvents.removeFirst();
            }
            return;
        }

        if (!bufferedEvents.isEmpty()) {
            Timber.i("dispatching %s buffered events", bufferedEvents.size());
            for (Pair<String, String> pair : bufferedEvents) {
                if (pair != null) {
                    dispatchJSEvent(pair.first, pair.second);
                }
            }
            bufferedEvents.clear();
        }

        dispatchJSEvent(eventName, stringifiedJSONValue);
    }

    public static void setInitialPushPayload(Map<String, Object> payload) {
        Timber.v("setInitialPushPayload()");
        if (initialPushPayload == null) {
            Timber.i("setInitialPushPayload() setting initial value");
            initialPushPayload = payload;
        }
    }

    public static void sendPushPayload(Map<String, Object> payload) {
        Timber.d("==> FCMPlugin sendPushPayload");
        try {
            JSONObject jo = new JSONObject();
            for (String key : payload.keySet()) {
                jo.put(key, payload.get(key));
                Timber.d("\tpayload: " + key + " => " + payload.get(key));
            }
            FCMPlugin.bufferJSEvent(EVENT_TYPE_NOTIFICATION, jo.toString());
        } catch (Exception e) {
            Timber.e(e, "\tERROR sendPushPayload: %s", e.getMessage());
        }
    }

    public static void sendTokenRefresh(String token) {
        Timber.d("==> FCMPlugin sendTokenRefresh");
        try {
            FCMPlugin.bufferJSEvent(EVENT_TYPE_TOKEN_REFRESH, "\"" + token + "\"");
        } catch (Exception e) {
            Timber.e(e, "\tERROR sendTokenRefresh: %s", e.getMessage());
        }
    }
}
