package com.hrs.firebase.messaging;

import static org.apache.cordova.BuildHelper.getBuildConfigValue;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;


import androidx.annotation.NonNull;

import com.hrs.firebase.messaging.incomingcall.Constants;
import com.hrs.firebase.messaging.incomingcall.IncomingCallService;

import org.json.JSONException;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import timber.log.Timber;

public class FCMPluginActivity extends Activity {

    private final CustomActivityLifecycleCallbacks lifecycleCallbacks = new CustomActivityLifecycleCallbacks();

    /*
     * this activity will be started if the user touches a notification that we own.
     * We send it's data off to the push plugin for processing.
     * If needed, we boot up the main activity to kickstart the application.
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        getApplication().registerActivityLifecycleCallbacks(lifecycleCallbacks);
        super.onCreate(savedInstanceState);
        Timber.d("==> FCMPluginActivity onCreate");
        Intent intent = getIntent();
        String action = intent.getAction();
        if (action != null && action.equals(Constants.ACTION_ANSWER_CALL)) {
            Intent callServiceIntent = new Intent(this, IncomingCallService.class);
            stopService(callServiceIntent);

            this.openCall(intent, true);
        } else {
            this.sendPushPayload();
        }

        bringMainActivityToFront();
        finish();
    }

    private void sendPushPayload() {
        Bundle intentExtras = getIntent().getExtras();
        if(intentExtras == null) {
            return;
        }

        int notificationId = intentExtras.getInt("notificationId");
        NotificationManager notificationManager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null && notificationId != -1) {
            notificationManager.cancel(notificationId);
        }

        try {
            if (notificationId != -1) {
                SharedPreferencesManager.getInstance(this).removeNotification(notificationId);
            }
        } catch (JSONException e) {
            Timber.e("Error trying to remove notification from shared preferences: %s", e.getMessage());
        }

        Timber.d("==> USER TAPPED NOTIFICATION");
        Map<String, Object> data;
        Boolean isKnoxManage = (Boolean) getBuildConfigValue(getApplicationContext(), "KNOXMANAGE");
        if (Boolean.TRUE.equals(isKnoxManage)) {
            data = Utils.bundleToHashMap((Bundle) Objects.requireNonNull(intentExtras.get("data")));
        } else {
            data = new HashMap<String, Object>();
            for (String key : intentExtras.keySet()) {
                Object value = intentExtras.get(key);
                Timber.d("\tKey: " + key + " Value: " + value);
                data.put(key, value);
            }
        }
        data.put("wasTapped", true);
        FCMPlugin.setInitialPushPayload(data);
        FCMPlugin.sendPushPayload(data);
    }

    private void forceMainActivityReload() {
        PackageManager pm = getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(getApplicationContext().getPackageName());
        startActivity(launchIntent);
    }

    private void bringMainActivityToFront() {
        PackageManager pm = getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(getApplicationContext().getPackageName());
        if (launchIntent != null) {
            // Set flag to bring the activity to the front instead of starting a new one
            Bundle options = new Bundle();
            options.putInt("android.activity.splashScreenStyle", 1);
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(launchIntent, options);
        }
    }

    private void openCall(Intent intent, boolean wasTapped) {
        Bundle bundle = intent.getBundleExtra("data");
        HashMap<String, Object> data = new HashMap<>();
        if (bundle != null) {
            data = Utils.bundleToHashMap(bundle);
        }

        // makes pcm answer the call
        data.put("wasTapped", wasTapped);
        FCMPlugin.sendPushPayload(data);

        Timber.d("FCMPlugin.getInstance().cordova.getActivity() " + FCMPlugin.getInstance());
        if (FCMPlugin.getInstance() == null) { // app isnt running yet
            Timber.d("Set initialPushPayload when the app isnt running");
            FCMPlugin.setInitialPushPayload(data);
        }
    }

    private void clearIncomingCall(Intent intent) {
        int notificationId = intent.getIntExtra("notificationId", -1);
        NotificationManager notificationManager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null && notificationId != -1) {
            notificationManager.cancel(notificationId);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Timber.d("==> FCMPluginActivity onResume");
        final NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();

//
//            View decorView = getWindow().getDecorView();
//            decorView.postInvalidate();

    }

    @Override
    public void onStart() {
        super.onStart();
        Timber.d("==> FCMPluginActivity onStart");
    }

    @Override
    public void onStop() {
        super.onStop();
        Timber.d("==> FCMPluginActivity onStop");
        View decorView = getWindow().getDecorView();
        decorView.postInvalidate();
    }

    public class CustomActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            Timber.i("onCreate(): %s", activity.getClass().getSimpleName());
        }

        @Override
        public void onActivityStarted(Activity activity) {
            Timber.i("onStart(): %s", activity.getClass().getSimpleName());
        }

        @Override
        public void onActivityResumed(Activity activity) {
            Timber.i("onResume(): %s", activity.getClass().getSimpleName());
        }

        @Override
        public void onActivityPaused(Activity activity) {
            Timber.i("onPause(): %s", activity.getClass().getSimpleName());
            Log.d("AB", "********* FCMPLUGIN ACTIVITY --->> ON PAUSE called -->> Relayouting");
            if (activity.getClass().getSimpleName().contains("MainActivity")) {
//                Log.d("AB", "--->> ON PAUSE called -->> Relayouting");
//                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
//                getWindow().getDecorView().postInvalidate();
//                getWindow().getDecorView().requestLayout();;
                new Handler(Looper.getMainLooper()).postDelayed(() -> { // TODO check for UI thread
                // Step 3: Finish current activity
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                getWindow().getDecorView().postInvalidate();
                getWindow().getDecorView().requestLayout();
            }, 1000);
            }
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, @NonNull Bundle outState) {
            Timber.i("onSaveInstanceState(): %s", activity.getClass().getSimpleName());
        }

        @Override
        public void onActivityStopped(Activity activity) {
            Timber.i("onStop(): %s", activity.getClass().getSimpleName());
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            Timber.i("onDestroy(): %s", activity.getClass().getSimpleName());
        }
    }

}
