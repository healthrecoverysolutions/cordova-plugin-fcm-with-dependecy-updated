package com.hrs.firebase.messaging;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.content.IntentFilter;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Objects;

import ionic.hrsmobile.byod.patient.dev2.R;

public class IncomingCallActivity extends AppCompatActivity {

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent incomingCallIntent = getIntent();
        Bundle data = incomingCallIntent.getBundleExtra("data");
        int notificationId = incomingCallIntent.getIntExtra("notificationId", 0);
        String caller = incomingCallIntent.getStringExtra("caller");
        String title = incomingCallIntent.getStringExtra("title");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        IntentFilter filter = new IntentFilter("FINISH_INCOMING_CALL_ACTIVITY");
        LocalBroadcastManager.getInstance(this).registerReceiver(finishReceiver, filter);

        IntentFilter callLeftFilter = new IntentFilter("CALL_LEFT");
        LocalBroadcastManager.getInstance(this).registerReceiver(finishReceiver, callLeftFilter);

        setContentView(R.layout.activity_incoming_call);

        hideSystemUI();
        TextView callerNameTextView = findViewById(R.id.caller_name);
        callerNameTextView.setText(caller);

        TextView callText = findViewById(R.id.call_title);
        callText.setText(title);

        findViewById(R.id.answer_button).setOnClickListener(view -> {
            Intent intent = new Intent(IncomingCallActivity.this, FCMPluginActivity.class);
            intent.setAction("ANSWER_CALL");
            intent.putExtra("data", data);
            intent.putExtra("notificationId", notificationId);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);

            finish();
        });

        findViewById(R.id.decline_button).setOnClickListener(view -> {
            Intent intent = new Intent(IncomingCallActivity.this, DeclineCallReceiver.class);
            intent.setAction("DECLINE_CALL");
            intent.putExtra("data", data);
            intent.putExtra("notificationId", notificationId);
            this.sendBroadcast(intent);

            finish();
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(finishReceiver);
        showSystemUI();
    }
    private final BroadcastReceiver finishReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            MyRingtoneManager.getInstance().stopRingtone();
            finish();
        }
    };

    private void hideSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Objects.requireNonNull(getWindow().getInsetsController()).hide(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
        }
    }

    private void showSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Objects.requireNonNull(getWindow().getInsetsController()).show(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
        }
    }
}
