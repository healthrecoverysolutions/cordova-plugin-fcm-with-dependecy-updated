package com.hrs.firebase.messaging;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import ionic.hrsmobile.byod.patient.dev2.R;

public class IncomingCallActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set your custom layout for the full-screen call screen
        setContentView(R.layout.activity_incoming_call);

        // Handle actions (e.g., Answer or Decline)
        findViewById(R.id.answer_button).setOnClickListener(view -> {
            // Logic for answering the call
            finish(); // Close the activity after answering
        });

        findViewById(R.id.decline_button).setOnClickListener(view -> {
            // Logic for declining the call
            finish(); // Close the activity after declining
        });
    }
}
