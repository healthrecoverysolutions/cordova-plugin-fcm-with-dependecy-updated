package com.gae.scaffolder.plugin;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import android.app.NotificationManager;
import android.content.Context;

import com.gae.scaffolder.plugin.interfaces.*;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
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
import java.util.Map;
import java.util.Objects;

import timber.log.Timber;

public class FCMPlugin extends CordovaPlugin {
    private static final String notificationEventName = "notification";
    private static final String tokenRefreshEventName = "tokenRefresh";
    private static Map<String, Object> initialPushPayload = null;
    protected Context context = null;
    protected static CallbackContext jsEventBridgeCallbackContext = null;

    @Override
    public void pluginInitialize() {
        super.pluginInitialize();
        Timber.d("==> FCMPlugin initialize");

        FirebaseMessaging.getInstance().subscribeToTopic("android");
        FirebaseMessaging.getInstance().subscribeToTopic("all");
    }

    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        Timber.d("==> FCMPlugin execute: %s", action);

        try {
            if (action.equals("ready")) {
                callbackContext.success();
            } else if (action.equals("startJsEventBridge")) {
                Timber.i("overridding event bridge");
                jsEventBridgeCallbackContext = callbackContext;
            } else if (action.equals("getToken")) {
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        getToken(callbackContext);
                    }
                });
            } else if (action.equals("getInitialPushPayload")) {
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        getInitialPushPayload(callbackContext);
                    }
                });
            } else if (action.equals("subscribeToTopic")) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        try {
                            FirebaseMessaging.getInstance().subscribeToTopic(args.getString(0));
                            callbackContext.success();
                        } catch (Exception e) {
                            callbackContext.error(e.getMessage());
                        }
                    }
                });
            } else if (action.equals("unsubscribeFromTopic")) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        try {
                            FirebaseMessaging.getInstance().unsubscribeFromTopic(args.getString(0));
                            callbackContext.success();
                        } catch (Exception e) {
                            callbackContext.error(e.getMessage());
                        }
                    }
                });
            } else if (action.equals("initDifferentAccount")) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        try {
                            if (!FirebaseApp.getApps(context).isEmpty()) {
                                FirebaseApp app = FirebaseApp.getInstance("[DEFAULT]");
                                app.delete();
                            }

                            Context context = cordova.getActivity();
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
                    }
                });
            } else if (action.equals("clearAllNotifications")) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        try {
                            Context context = cordova.getActivity();
                            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                            nm.cancelAll();
                            callbackContext.success();
                        } catch (Exception e) {
                            callbackContext.error(e.getMessage());
                        }
                    }
                });
            } else if (action.equals("createNotificationChannel")) {
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        new FCMPluginChannelCreator(getContext()).createNotificationChannel(callbackContext, args);
                    }
                });
            } else if (action.equals("deleteInstanceId")) {
                this.deleteInstanceId(callbackContext);
            } else if (action.equals("hasPermission")) {
                this.hasPermission(callbackContext);
            } else {
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
        if(initialPushPayload == null) {
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
        } catch(Exception error) {
            try {
                callback.error(exceptionToJson(error));
            }
            catch (JSONException jsonErr) {
                Timber.e(jsonErr, "Error when parsing json");
            }
        }
    }

    public void getToken(final TokenListeners<String, JSONObject> callback) {
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener<String>() {
                @Override
                public void onComplete(@NonNull Task<String> task) {
                    if (!task.isSuccessful()) {
                        Timber.e(task.getException(), "getInstanceId failed");
                        try {
                            callback.error(exceptionToJson(Objects.requireNonNull(task.getException())));
                        }
                        catch (JSONException jsonErr) {
                            Timber.e(jsonErr, "Error when parsing json");
                        }
                        return;
                    }

                    // Get new Instance ID token
                    String newToken = task.getResult();

                    Timber.i("\tToken: %s", newToken);
                    callback.success(newToken);
                }
            });

            FirebaseMessaging.getInstance().getToken().addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull final Exception e) {
                    try {
                        Timber.e(e, "Error retrieving token: ");
                        callback.error(exceptionToJson(e));
                    } catch (JSONException jsonErr) {
                        Timber.e(jsonErr, "Error when parsing json");
                    }
                }
            });
        } catch (Exception e) {
            Timber.e(e, "\tError retrieving token");
            try {
                callback.error(exceptionToJson(e));
            } catch(JSONException ignored) {}
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
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    NotificationManagerCompat notificationManagerCompat =
                        NotificationManagerCompat.from(cordova.getActivity().getApplicationContext());
                    callbackContext.success(notificationManagerCompat.areNotificationsEnabled() ? 1 : 0);
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
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

    private static void dispatchJSEvent(String eventName, String stringifiedJSONValue) throws Exception {
        if(FCMPlugin.jsEventBridgeCallbackContext == null) {
            Timber.d("\tUnable to send event due to unreachable bridge context");
            return;
        }
        String jsEventData = "[\"" + eventName + "\"," + stringifiedJSONValue + "]";
        PluginResult dataResult = new PluginResult(PluginResult.Status.OK, jsEventData);
        dataResult.setKeepCallback(true);
        FCMPlugin.jsEventBridgeCallbackContext.sendPluginResult(dataResult);
        Timber.d("\tSent event: " + eventName + " with " + stringifiedJSONValue);
    }

    public static void setInitialPushPayload(Map<String, Object> payload) {
        if(initialPushPayload == null) {
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
            FCMPlugin.dispatchJSEvent(notificationEventName, jo.toString());
        } catch (Exception e) {
            Timber.e(e, "\tERROR sendPushPayload: %s", e.getMessage());
        }
    }

    public static void sendTokenRefresh(String token) {
        Timber.d("==> FCMPlugin sendTokenRefresh");
        try {
            FCMPlugin.dispatchJSEvent(tokenRefreshEventName, "\"" + token + "\"");
        } catch (Exception e) {
            Timber.e(e, "\tERROR sendTokenRefresh: %s", e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        Timber.i("onDestroy()");
        initialPushPayload = null;
        jsEventBridgeCallbackContext = null;
    }

    protected Context getContext() {
        context = cordova != null ? cordova.getActivity().getBaseContext() : context;
        if (context == null) {
            throw new RuntimeException("The Android Context is required. Verify if the 'activity' or 'context' are passed by constructor");
        }

        return context;
    }
}
