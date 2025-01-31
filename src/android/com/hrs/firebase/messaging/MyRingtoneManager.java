package com.hrs.firebase.messaging;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

public class MyRingtoneManager {

    private static MyRingtoneManager instance;
    private MediaPlayer mediaPlayer;
    private final Handler handler;
    private boolean isPlaying;

    private MyRingtoneManager() {
        // Private constructor to enforce singleton
        Looper.prepare();
        handler = new Handler();
    }

    public static MyRingtoneManager getInstance() {
        if (instance == null) {
            instance = new MyRingtoneManager();
        }
        return instance;
    }

    public void playRingtone(Context context, Uri ringtoneResource, int delayBetweenLoops) {
        stopRingtone(); // Ensure no other ringtone is playing
        mediaPlayer = MediaPlayer.create(context, ringtoneResource);
        isPlaying = true;

        mediaPlayer.setOnCompletionListener(mp -> {
            // Add a delay before replaying the ringtone
            if (isPlaying) {
                handler.postDelayed(() -> {
                    if (isPlaying) {
                        mediaPlayer.start(); // Restart playback
                    }
                }, delayBetweenLoops);
            }
        });

        mediaPlayer.start(); // Start playing the ringtone
    }

    public void stopRingtone() {
        if (mediaPlayer != null) {
            isPlaying = false;
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
