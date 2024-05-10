package com.gae.scaffolder.plugin;

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
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

import timber.log.Timber;

public class FCMPlugin extends CordovaPlugin {
    public static String notificationEventName = "notification";
    public static String tokenRefreshEventName = "tokenRefresh";
    public static Map<String, Object> initialPushPayload;
    private static FCMPlugin instance;
    protected Context context;
    protected static CallbackContext jsEventBridgeCallbackContext;

    public FCMPlugin() {}
    public FCMPlugin(Context context) {
        this.context = context;
    }

    public static synchronized FCMPlugin getInstance(Context context) {
        if (instance == null) {
            instance = new FCMPlugin(context);
            instance = getPlugin(instance);
        }

        return instance;
    }

    public static synchronized FCMPlugin getInstance() {
        if (instance == null) {
            instance = new FCMPlugin();
            instance = getPlugin(instance);
        }

        return instance;
    }

    public static FCMPlugin getPlugin(FCMPlugin plugin) {
        if (plugin.webView != null) {
            instance = (FCMPlugin) plugin.webView.getPluginManager().getPlugin(FCMPlugin.class.getName());
        } else {
            plugin.initialize(null, null);
            instance = plugin;
        }

        return instance;
    }

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        Timber.d("==> FCMPlugin initialize");

        FirebaseMessaging.getInstance().subscribeToTopic("android");
        FirebaseMessaging.getInstance().subscribeToTopic("all");
    }

    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        Timber.d("==> FCMPlugin execute: " + action);

        try {
            if (action.equals("ready")) {
                Timber.d("=> FCMPlugin ready", callbackContext);
                callbackContext.success();
            } else if (action.equals("startJsEventBridge")) {
                Timber.d("=> FCMPlugin startJsEventBridge", callbackContext);
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
            Timber.d("ERROR: onPluginAction: " + e.getMessage());
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
                public void onComplete(Task<String> task) {
                    if (!task.isSuccessful()) {
                        Timber.w("getInstanceId failed", task.getException());
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

                    Timber.i("\tToken: " + newToken);
                    callback.success(newToken);
                }
            });

            FirebaseMessaging.getInstance().getToken().addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(final Exception e) {
                    try {
                        Timber.e(e, "Error retrieving token: ");
                        callback.error(exceptionToJson(e));
                    } catch (JSONException jsonErr) {
                        Timber.e(jsonErr, "Error when parsing json");
                    }
                }
            });
        } catch (Exception e) {
            Timber.w("\tError retrieving token", e);
            try {
                callback.error(exceptionToJson(e));
            } catch(JSONException je) {}
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
                put("stacktrace", exception.getStackTrace().toString());
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
        String jsEventData = "[\"" + eventName + "\"," + stringifiedJSONValue + "]";
        PluginResult dataResult = new PluginResult(PluginResult.Status.OK, jsEventData);
        dataResult.setKeepCallback(true);
        if(FCMPlugin.jsEventBridgeCallbackContext == null) {
            Timber.d("\tUnable to send event due to unreachable bridge context");
            return;
        }
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
            Timber.d("\tERROR sendPushPayload: " + e.getMessage());
        }
    }

    public static void sendTokenRefresh(String token) {
        Timber.d("==> FCMPlugin sendTokenRefresh");
        try {
            FCMPlugin.dispatchJSEvent(tokenRefreshEventName, "\"" + token + "\"");
        } catch (Exception e) {
            Timber.d("\tERROR sendTokenRefresh: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
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
