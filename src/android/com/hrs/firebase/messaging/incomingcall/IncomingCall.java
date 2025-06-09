package com.hrs.firebase.messaging.incomingcall;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import org.json.JSONObject;

public class IncomingCall {
    public static void showNotification(Context context, String callerName, String callTitle, Bundle data) {
        Intent intent = new Intent(context, IncomingCallService.class);
        intent.putExtra(Constants.EXTRA_CALLER_NAME, callerName);
        intent.putExtra(Constants.EXTRA_CALL_TITLE, callTitle);
        intent.putExtra(Constants.EXTRA_CALL_DATA, data);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void callLeft(Context context) {
        Intent callLeftIntent = new Intent(context, IncomingCallService.class);
        callLeftIntent.setAction(Constants.ACTION_CALL_LEFT);
        context.startService(callLeftIntent);
    }
}
