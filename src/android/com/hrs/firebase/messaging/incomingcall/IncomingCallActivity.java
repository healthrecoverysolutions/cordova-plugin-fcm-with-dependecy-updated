package com.hrs.firebase.messaging.incomingcall;

import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.hrs.firebase.messaging.FCMPluginActivity;
import com.samsung.android.knox.EnterpriseDeviceManager;
import com.samsung.android.knox.EnterpriseKnoxManager;
import com.samsung.android.knox.kiosk.KioskMode;

public class IncomingCallActivity extends AppCompatActivity {
    private Bundle extras;
    private String action;

    private final BroadcastReceiver callActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Constants.ACTION_CALL_LEFT.equals(intent.getAction()) ||
                Constants.ACTION_CALL_TIMEOUT.equals(intent.getAction())
            ) {
                restoreSystemUi();
                finish();
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUI();

        setContentView(getResources().getIdentifier("activity_incoming_call", "layout", getPackageName()));

        String callerName = getIntent().getStringExtra(Constants.EXTRA_CALLER_NAME);
        String callTitle = getIntent().getStringExtra(Constants.EXTRA_CALL_TITLE);
        extras = getIntent().getExtras();

        int callerNameId = getResources().getIdentifier("caller_name", "id", getPackageName());
        TextView callerNameView = findViewById(callerNameId);

        int callTitleId = getResources().getIdentifier("call_title", "id", getPackageName());
        TextView callTitleView = findViewById(callTitleId);

        callerNameView.setText(callerName);
        callTitleView.setText(callTitle);

        // Wake up the device and show the activity
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        int answerButtonId = getResources().getIdentifier("answer_button", "id", getPackageName());
        findViewById(answerButtonId).setOnClickListener(v -> {
            action = Constants.ACTION_ANSWER_CALL;
            restoreSystemUi();
            finish();
        });

        int declineButtonId = getResources().getIdentifier("decline_button", "id", getPackageName());
        findViewById(declineButtonId).setOnClickListener(v -> {
            Intent intent = new Intent(this, IncomingCallService.class);
            intent.setAction(Constants.ACTION_DECLINE_CALL);
            intent.putExtras(extras);
            startService(intent);
            restoreSystemUi();
            finish();
        });

        LocalBroadcastManager.getInstance(this).registerReceiver(
            callActionReceiver, new IntentFilter(Constants.ACTION_CALL_LEFT));

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(callActionReceiver);
        if (action != null && action.equals(Constants.ACTION_ANSWER_CALL)) {
            Intent incomingCallIntent = getIntent();
            Bundle data = incomingCallIntent.getBundleExtra(Constants.EXTRA_CALL_DATA);
            int notificationId = incomingCallIntent.getIntExtra("notificationId", -1);
            Intent intent = new Intent(IncomingCallActivity.this, FCMPluginActivity.class);
            intent.setAction(Constants.ACTION_ANSWER_CALL);
            intent.putExtra("data", data);
            intent.putExtra("notificationId", notificationId);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            action = null;
        }
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    private void restoreSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }
}
