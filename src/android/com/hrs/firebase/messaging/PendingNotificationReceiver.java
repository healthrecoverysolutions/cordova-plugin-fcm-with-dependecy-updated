package com.hrs.firebase.messaging;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.messaging.RemoteMessage;

public class PendingNotificationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        RemoteMessage message = intent.getParcelableExtra("message");
        Log.e("PendingNotificationHelper", "----->> ON RECIEVE ---->>" + context);
        if(message.getNotification() != null){
            Log.e("PendingNotificationReceiver", "	Notification Title: " + message.getNotification().getTitle());
            Log.e("PendingNotificationReceiver","	Notification Message: " + message.getNotification().getBody());
        }
        NotificationHelper.showNotification(context, message);
    }
}

//    NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
//notificationManager.cancelAll();
// use cases :
// what if there are multiple calls back to back which all to cancel and which all not. Unnecessary processing will have to be done.
// to access notifications from notification tray, we have to allow special notification access to our applicatio0n. We have to direct the user to that setting and let user allow that.
