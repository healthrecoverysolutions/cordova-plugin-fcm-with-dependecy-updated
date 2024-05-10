package com.gae.scaffolder.plugin;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import android.app.NotificationManager;
import android.content.Context;
import android.util.Pair;

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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.Map;

import timber.log.Timber;

public class FCMPlugin extends CordovaPlugin {
    private static final String notificationEventName = "notification";
    private static final String tokenRefreshEventName = "tokenRefresh";
    private static FCMPlugin instance = null;
    private static LinkedList<Pair<String, String>> messageBuffer = new LinkedList<>();
    private Map<String, Object> initialPushPayload;
    private CallbackContext jsEventBridgeCallbackContext;

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
        super.onDestroy();
        instance = null;
    }

    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        Timber.d("==> FCMPlugin execute: %s", action);

        try {
            if (action.equals("ready")) {
                callbackContext.success();
            } else if (action.equals("startJsEventBridge")) {
                this.jsEventBridgeCallbackContext = callbackContext;
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
                              if (!FirebaseApp.getApps(cordova.getContext()).isEmpty()) {
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
                        new FCMPluginChannelCreator(cordova.getContext()).createNotificationChannel(callbackContext, args);
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
            Timber.e(e, "ERROR: onPluginAction");
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
                            callback.error(exceptionToJson(task.getException()));
                        }
                        catch (JSONException jsonErr) {
                            Timber.e(jsonErr, "Error when parsing json");
                        }
                        return;
                    }

                    // Get new Instance ID token
                    String newToken = task.getResult();
                    Timber.i("Token: %s", newToken);
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
            Timber.e(e, "Error retrieving token");
            try {
                callback.error(exceptionToJson(e));
            } catch(JSONException je) {
                Timber.e(e, "Error converting exception to json");
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

    private static String exceptionStacktraceToString(Exception e)
    {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outStream);
        e.printStackTrace(printStream);
        printStream.close();
        return outStream.toString();
    }

    private static JSONObject exceptionToJson(final Exception exception) throws JSONException {
        if (exception != null) {
            return new JSONObject()
                .put("message", exception.getMessage())
                .put("cause", exception.getClass().getName())
                .put("stacktrace", exceptionStacktraceToString(exception));
        } else {
            return new JSONObject()
                .put("message", "unknown exception occurred");
        }
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

    private static boolean hasValidInstance() {
        return FCMPlugin.instance != null
            && FCMPlugin.instance.jsEventBridgeCallbackContext != null;
    }

    private static void dispatchJSEvent(String eventName, String stringifiedJSONValue) {
        String jsEventData = "[\"" + eventName + "\"," + stringifiedJSONValue + "]";
        PluginResult dataResult = new PluginResult(PluginResult.Status.OK, jsEventData);
        dataResult.setKeepCallback(true);
        if (!hasValidInstance()) {
            Timber.d("\tUnable to send event due to unreachable bridge context");
            return;
        }
        FCMPlugin.instance.jsEventBridgeCallbackContext.sendPluginResult(dataResult);
        Timber.d("Sent event: %s with %s", eventName, stringifiedJSONValue);
    }

    // Prevent messageBuffer from growing infinitely
    private static void purgeExcessMessages() {
        final int maxBufferSize = 1000;
        final int currentBufferSize = messageBuffer.size();

        if (messageBuffer.size() <= maxBufferSize) {
            return;
        }

        Timber.w("enqueueJSEvent message buffer has exceeded %s entries! (current is %s)", maxBufferSize, currentBufferSize);

        while (messageBuffer.size() > maxBufferSize) {
            messageBuffer.removeFirst();
        }
    }

    private static void enqueueJSEvent(String eventName, String stringifiedJSONValue) {
        if (!hasValidInstance()) {
            Timber.d("enqueueJSEvent() saving to buffer until plugin is ready");
            messageBuffer.add(new Pair<>(eventName, stringifiedJSONValue));
            purgeExcessMessages();
            return;
        }

        Timber.d("enqueueJSEvent() flushing %s buffered event(s)", messageBuffer.size());
        Pair<String, String> buffered = messageBuffer.removeFirst();

        // flush all events that were buffered before the plugin was ready,
        // or if the plugin had to be regenerated at some point
        while (buffered != null) {
            dispatchJSEvent(buffered.first, buffered.second);
            buffered = messageBuffer.removeFirst();
        }

        Timber.d("enqueueJSEvent() dispatching most recent event");
        dispatchJSEvent(eventName, stringifiedJSONValue);
    }

    public static void setInitialPushPayload(Map<String, Object> payload) {
        if(instance != null && instance.initialPushPayload == null) {
            instance.initialPushPayload = payload;
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
            FCMPlugin.enqueueJSEvent(notificationEventName, jo.toString());
        } catch (Exception e) {
            Timber.e(e, "ERROR sendPushPayload");
        }
    }

    public static void sendTokenRefresh(String token) {
        Timber.d("==> FCMPlugin sendTokenRefresh");
        try {
            FCMPlugin.enqueueJSEvent(tokenRefreshEventName, "\"" + token + "\"");
        } catch (Exception e) {
            Timber.e(e, "ERROR sendTokenRefresh");
        }
    }
}
