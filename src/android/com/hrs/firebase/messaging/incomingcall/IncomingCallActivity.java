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
        Log.d("AB", "----->> ON CREATE hiding system UI");

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
//            new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                Intent incomingCallIntent = getIntent();
//                Bundle data = incomingCallIntent.getBundleExtra(Constants.EXTRA_CALL_DATA);
//                int notificationId = incomingCallIntent.getIntExtra("notificationId", -1);
//                Intent intent = new Intent(IncomingCallActivity.this, FCMPluginActivity.class);
//                intent.setAction(Constants.ACTION_ANSWER_CALL);
//                intent.putExtra("data", data);
//                intent.putExtra("notificationId", notificationId);
//                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
//                startActivity(intent);
//                finish();
//                 }, 300);

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

                    getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(int visibility) {
                    Log.d("AB", "---->>> On System UI Visibility Change " + visibility);
                    if(visibility == 0) {
                        if (action != null && action.equals(Constants.ACTION_ANSWER_CALL)) {
                            // Step 2: Give system a short delay to redraw nav/status bars
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            Log.d("AB", "---->>> On System UI Visibility Change answer call action");
                            //restoreSystemUi();
                            Intent incomingCallIntent = getIntent();
                            Bundle data = incomingCallIntent.getBundleExtra(Constants.EXTRA_CALL_DATA);
                            int notificationId = incomingCallIntent.getIntExtra("notificationId", -1);
                            Intent intent = new Intent(IncomingCallActivity.this, FCMPluginActivity.class);
                            intent.setAction(Constants.ACTION_ANSWER_CALL);
                            intent.putExtra("data", data);
                            intent.putExtra("notificationId", notificationId);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

                            startActivity(intent);

                            // Step 2: Give system a short delay to redraw nav/status bars
                           // new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                // Step 3: Finish current activity
                                finish();
                                action = null;
//                                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
//
//                                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                            }, 300);
                           // finish(); // TODO thode delay se
                        }
                    }
                }
            });

    }




    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(callActionReceiver);
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
        getWindow().getDecorView().postInvalidate();
        // getWindow().getDecorView().requestLayout();
    }
}


//    package com.hrs.firebase.messaging.incomingcall;
//
//    import android.app.ActivityManager;
//    import android.app.PendingIntent;
//    import android.content.BroadcastReceiver;
//    import android.content.Context;
//    import android.content.Intent;
//    import android.content.IntentFilter;
//    import android.os.Build;
//    import android.os.Bundle;
//    import android.os.Handler;
//    import android.os.Looper;
//    import android.util.Log;
//    import android.view.KeyEvent;
//    import android.view.View;
//    import android.view.WindowInsets;
//    import android.view.WindowInsetsController;
//    import android.view.WindowManager;
//    import android.widget.TextView;
//
//    import androidx.appcompat.app.AppCompatActivity;
//    import androidx.localbroadcastmanager.content.LocalBroadcastManager;
//
//    import com.hrs.firebase.messaging.FCMPluginActivity;
//    import com.samsung.android.knox.EnterpriseDeviceManager;
//    import com.samsung.android.knox.EnterpriseKnoxManager;
//    import com.samsung.android.knox.kiosk.KioskMode;
//
//    public class IncomingCallActivity extends AppCompatActivity {
//        private Bundle extras;
//        private Intent intent;
//        private String action;
//
//        private final BroadcastReceiver callActionReceiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                if (Constants.ACTION_CALL_LEFT.equals(intent.getAction()) ||
//                    Constants.ACTION_CALL_TIMEOUT.equals(intent.getAction())
//                ) {
//                    restoreSystemUi();
//                    finish();
//                }
//            }
//        };
//
//
//        @Override
//        protected void onCreate(Bundle savedInstanceState) {
//            super.onCreate(savedInstanceState);
//            hideSystemUI();
////            EnterpriseDeviceManager ekm = EnterpriseDeviceManager.getInstance(this);
////            KioskMode kiosk = ekm.getKioskMode();
////            // kiosk.allowTaskManager(false);
////            kiosk.hideNavigationBar(true);
////            kiosk.hideStatusBar(true);
////            // startLockTask();
//
//            setContentView(getResources().getIdentifier("activity_incoming_call", "layout", getPackageName()));
//            Log.d("AB", "----->> ON CREATE hiding system UI");
//
//            String callerName = getIntent().getStringExtra(Constants.EXTRA_CALLER_NAME);
//            String callTitle = getIntent().getStringExtra(Constants.EXTRA_CALL_TITLE);
//            // intent = getIntent();
//            extras = getIntent().getExtras();
//
//            int callerNameId = getResources().getIdentifier("caller_name", "id", getPackageName());
//            TextView callerNameView = findViewById(callerNameId);
//
//            int callTitleId = getResources().getIdentifier("call_title", "id", getPackageName());
//            TextView callTitleView = findViewById(callTitleId);
//
//            callerNameView.setText(callerName);
//            callTitleView.setText(callTitle);
//
//            // Wake up the device and show the activity
//            getWindow().addFlags(
//                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
//                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
//                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
//            );
//
//            getWindow().setFlags(
//                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
//                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
//            );
//
//            int answerButtonId = getResources().getIdentifier("answer_button", "id", getPackageName());
//            findViewById(answerButtonId).setOnClickListener(v -> {
//                  action = Constants.ACTION_ANSWER_CALL;
//                  restoreSystemUi();
//
////                // Step 2: Give system a short delay to redraw nav/status bars
////                new Handler(Looper.getMainLooper()).postDelayed(() -> {
////                    // Step 3: Finish current activity
////                    finish();
////
////                    // Step 4: Launch next activity
////                    Intent incomingCallIntent = getIntent();
////                Bundle data = incomingCallIntent.getBundleExtra(Constants.EXTRA_CALL_DATA);
////                int notificationId = incomingCallIntent.getIntExtra("notificationId", -1);
////                Intent intent = new Intent(IncomingCallActivity.this, FCMPluginActivity.class);
////                intent.setAction(Constants.ACTION_ANSWER_CALL);
////                intent.putExtra("data", data);
////                intent.putExtra("notificationId", notificationId);
////                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
////                startActivity(intent);
////
////                }, 300);
////                  finish();
//                //restoreSystemUi();
////                Intent incomingCallIntent = getIntent();
////                Bundle data = incomingCallIntent.getBundleExtra(Constants.EXTRA_CALL_DATA);
////                int notificationId = incomingCallIntent.getIntExtra("notificationId", -1);
////                Intent intent = new Intent(IncomingCallActivity.this, FCMPluginActivity.class);
////                intent.setAction(Constants.ACTION_ANSWER_CALL);
////                intent.putExtra("data", data);
////                intent.putExtra("notificationId", notificationId);
////                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
////                // restoreSystemUi();
////                startActivity(intent);
////                // restoreSystemUi();
////                finish();
//            });
//
//            int declineButtonId = getResources().getIdentifier("decline_button", "id", getPackageName());
//            findViewById(declineButtonId).setOnClickListener(v -> {
//                Intent intent = new Intent(this, IncomingCallService.class);
//                intent.setAction(Constants.ACTION_DECLINE_CALL);
//                intent.putExtras(extras);
//                startService(intent);
//                //restoreSystemUi();
//                finish();
//            });
//
//            LocalBroadcastManager.getInstance(this).registerReceiver(
//                callActionReceiver, new IntentFilter(Constants.ACTION_CALL_LEFT));
//
//
////            /**
////             * Still issue occurs
////             */
//            getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
//                @Override
//                public void onSystemUiVisibilityChange(int visibility) {
//                    Log.d("AB", "---->>> On System UI Visibility Change " + visibility);
//                    if(visibility == 0) {
//                        if (action != null && action.equals(Constants.ACTION_ANSWER_CALL)) {
//                            // Step 2: Give system a short delay to redraw nav/status bars
//                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                            Log.d("AB", "---->>> On System UI Visibility Change answer call action");
//                            //restoreSystemUi();
//                            Intent incomingCallIntent = getIntent();
//                            Bundle data = incomingCallIntent.getBundleExtra(Constants.EXTRA_CALL_DATA);
//                            int notificationId = incomingCallIntent.getIntExtra("notificationId", -1);
//                            Intent intent = new Intent(IncomingCallActivity.this, FCMPluginActivity.class);
//                            intent.setAction(Constants.ACTION_ANSWER_CALL);
//                            intent.putExtra("data", data);
//                            intent.putExtra("notificationId", notificationId);
//                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
//
//                            startActivity(intent);
//
//                            // Step 2: Give system a short delay to redraw nav/status bars
//                           // new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                                // Step 3: Finish current activity
//                                finish();
//                                action = null;
////                                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
////
////                                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
//                            }, 300);
//                           // finish(); // TODO thode delay se
//                        }
//                    }
//                }
//            });
//        }
//
//        @Override
//        public void onBackPressed() {
//           // super.onBackPressed();
//        }
//
//        @Override
//        public boolean dispatchKeyEvent(KeyEvent event) {
//            int keyCode = event.getKeyCode();
//            if (keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
//                return true; // Block home & app switch keys
//            }
//            return super.dispatchKeyEvent(event);
//        }
//
////        @Override
////        protected void onResume() {
////            super.onResume();
////            startLockTask();
////        }
//
//        @Override
//        protected void onDestroy() {
//            super.onDestroy();
//            LocalBroadcastManager.getInstance(this).unregisterReceiver(callActionReceiver);
////            if (action != null && action.equals(Constants.ACTION_ANSWER_CALL)) {
//////                Intent intent = new Intent(this, IncomingCallService.class);
//////                intent.setAction(Constants.ACTION_ANSWER_CALL);
//////                intent.putExtras(extras);
//////                startService(intent);
////                Intent incomingCallIntent = getIntent();
////                Bundle data = incomingCallIntent.getBundleExtra(Constants.EXTRA_CALL_DATA);
////                int notificationId = incomingCallIntent.getIntExtra("notificationId", -1);
////                Intent intent = new Intent(IncomingCallActivity.this, FCMPluginActivity.class);
////                intent.setAction(Constants.ACTION_ANSWER_CALL);
////                intent.putExtra("data", data);
////                intent.putExtra("notificationId", notificationId);
////                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
////                startActivity(intent);
////            }
//        }
//
//
////        @Override
////        protected void onStop() {
////            super.onStop();
////            Log.d("AB", "----->> ON STOP restoring system UI");
////            restoreSystemUi();
////        }
//
//        private void hideSystemUI() {
//
//            getWindow().getDecorView().setSystemUiVisibility(
//                View.SYSTEM_UI_FLAG_IMMERSIVE
//                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
//                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                    | View.SYSTEM_UI_FLAG_FULLSCREEN
//            );
////            getWindow().getDecorView().setSystemUiVisibility(
////                     View.SYSTEM_UI_FLAG_LAYOUT_STABLE
////                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
////                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
////                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
////                    | View.SYSTEM_UI_FLAG_FULLSCREEN
////            );
//        }
////
//        private void restoreSystemUi() {
//
////            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
////                WindowInsetsController controller = getWindow().getInsetsController();
////                if (controller != null) {
////                    controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
////                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_DEFAULT);
////                }
////            } else {
//
//            //NEHAL
//            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
////            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
////            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
//            //}
//            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
//            getWindow().getDecorView().postInvalidate();
//            //getWindow().getDecorView().invalidate();
//            getWindow().getDecorView().requestLayout();;
//
//
////            View decorView = getWindow().getDecorView();
////            decorView.setSystemUiVisibility(
////                View.SYSTEM_UI_FLAG_VISIBLE | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
////            );
////            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//            //getWindow().addFlags(
//            //                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
//            //                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
//            //                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
//            //            );
////            View decorView = getWindow().getDecorView();
////            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
////            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
////            getWindow().clearFlags(
////                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
////                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
////                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
////            );
//
//        }
//    }
//
