package com.hrs.firebase.messaging;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import timber.log.Timber;

public class FCMPluginActivity extends Activity {
    /*
     * this activity will be started if the user touches a notification that we own.
     * We send it's data off to the push plugin for processing.
     * If needed, we boot up the main activity to kickstart the application.
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Timber.d("==> FCMPluginActivity onCreate");
        Intent intent = getIntent();
        String action = intent.getAction();
        if (action != null && action.equals("ANSWER_CALL")) {
            this.openCall(intent, true);
        } else if (action != null && action.equals("OPEN_CALL")) {
            this.openCall(intent, false);
        } else {
            this.sendPushPayload();
        }

        forceMainActivityReload();
        finish();
    }

    private void sendPushPayload() {
        Bundle intentExtras = getIntent().getExtras();
        if(intentExtras == null) {
            return;
        }
        Timber.d("==> USER TAPPED NOTIFICATION");
        Map<String, Object> data = Utils.bundleToHashMap((Bundle) Objects.requireNonNull(intentExtras.get("data")));
        data.put("wasTapped", true);
        FCMPlugin.setInitialPushPayload(data);
        FCMPlugin.sendPushPayload(data);
    }

    private void forceMainActivityReload() {
        PackageManager pm = getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(getApplicationContext().getPackageName());
        startActivity(launchIntent);
    }

    private void openCall(Intent intent, boolean wasTapped) {
        Bundle bundle = intent.getBundleExtra("data");
        HashMap<String, Object> data = new HashMap<>();
        if (bundle != null) {
            data = Utils.bundleToHashMap(bundle);
        }

        // makes pcm answer the call
        data.put("wasTapped", wasTapped);

       clearIncomingCall(intent);

        FCMPlugin.setInitialPushPayload(data);
        FCMPlugin.sendPushPayload(data);
        forceMainActivityReload();
    }

    private void declineCall(Intent intent) {
        // TODO: FIX DATA
        FCMPlugin.sendCallDeclined(new HashMap<>());
        clearIncomingCall(intent);
    }

    private void clearIncomingCall(Intent intent) {
        MyRingtoneManager.getInstance().stopRingtone();
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
    }

}
