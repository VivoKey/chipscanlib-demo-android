package com.vivokey.chipscanlibtest;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.vivokey.chipscanlib.VivoAuthResult;
import com.vivokey.chipscanlib.VivoAuthenticator;
import com.vivokey.chipscanlib.VivoTag;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    /**
     * These are the main variables required
     */

    // Represents the phone's NFC adapter
    private NfcAdapter mNfcAdapter;
    // Represents a Handler - gets used to poll the Chipscan library to see when it's finished
    private Handler mHandler;
    // Represents the Tag object received on an Intent with a NFC event in it
    private Tag mCurrentTag;
    // Becomes the authResult from the library, once authentication is complete
    VivoAuthResult authResult;
    // Is the main part of the library used here
    VivoAuthenticator auth;
    // Represents the Tag, but as a VivoTag
    VivoTag vivotag;
    // Some View stuff
    ProgressBar running;
    TextView mainText;
    TextView tv3;
    Thread getChallAuth = new Thread(() -> {
        auth.getChallenge();
    });

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /**
         * Fired when the Activity is created. We set up the GUI and grab a new Authenticator object.
         */
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        // You need to put your own API key here. This one is likely to be disabled.
        auth = new VivoAuthenticator("");
        running = findViewById(R.id.progressBar);
        mainText = findViewById(R.id.textView);
        tv3 = findViewById(R.id.textView3);
        mHandler = new Handler();
        mainText.setText("Waiting for tag...");
        // Starts a challenge checker
        getChallAuth.start();
    }

    @Override
    protected void onResume() {
        /**
         * After we get resumed from a pause. Just sees if we have a pending intent, resets the
         * adapter and re-enables dispatch.
         */
        super.onResume();
        Intent nfcIntent = new Intent(this, getClass());
        nfcIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, nfcIntent, 0);
        IntentFilter[] intentFiltersArray = new IntentFilter[]{};
        String[][] techList = new String[][]{{android.nfc.tech.Ndef.class.getName()}, {android.nfc.tech.NdefFormatable.class.getName()}};
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        mNfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techList);
        getChallAuth.start();
    }

    @Override
    protected void onPause() {
        /**
         * Runs before we pause. Just disables the dispatch.
         */
        super.onPause();
        mNfcAdapter.disableForegroundDispatch(this);
    }

    /**
     * This is the polling service. It uses the handler to schedule itself to run every .5 seconds.
     *
     * Copy it as-is, honestly.
     */
    Runnable mStatusChecker = new Runnable() {
        @Override
        public void run() {
            try {
                checkApi(); //this function can change value of mInterval.
            } finally {
                // 100% guarantee that this always happens, even if
                // your update method throws an exception
                mHandler.postDelayed(mStatusChecker, 500);
            }
        }
    };

    @SuppressLint("SetTextI18n")
    void checkApi() {
        /**
         * The function called by the polling service.
         *
         * Once it determines the auth is finished, it processes it. Basically, pull the result and
         * check the output. This shows how to work the 4 separate statuses an auth can have.
         */
        // Poll the auth thread to check if it's finished
        if (auth.isFinished()) {
            // Finished
            stopRepeatingTask();
            authResult = auth.getResult();
            mainText.setText("Auth processed. Chip UID: " + vivotag.getUid());
            running.setVisibility(View.INVISIBLE);
            tv3.setText("Result: ");
            if(authResult.getIDtype() == "member") {
                // Member ID
                tv3.append("Member.\n ID:" + authResult.getMemberid());
            } else if(authResult.getIDtype() == "chip") {
                tv3.append("Chip. \n ID:" + authResult.getChipid());

            } else if(authResult.getIDtype() == "chip-member") {
                tv3.append("Chip/Member. \n Chip ID:" + authResult.getChipid());
                tv3.append("\n Member ID: " +authResult.getMemberid());
            } else {
                tv3.append("Error.");
            }

        }
    }

    /**
     * Stub function to start the polling.
     */
    void startRepeatingTask() {
        mStatusChecker.run();
    }

    /**
     * Stub function to stop polling.
     */
    void stopRepeatingTask() {
        mHandler.removeCallbacks(mStatusChecker);
    }

    @SuppressLint({"MissingSuperCall", "SetTextI18n"})
    @Override
    public void onNewIntent(Intent intent) {
        /**
         * When we get an intent. Probably should call super, but it works like this.
         *
         * Just grabs the tag if there is one and sets the VivoKey API up, starting it running.
         * 
         */

        try {
            mCurrentTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            // Have a tag
            // Do the thing, open the API up
            vivotag = new VivoTag(mCurrentTag);
            auth.setTag(vivotag);
            auth.start();
            startRepeatingTask();
            running.setVisibility(View.VISIBLE);
            mainText.setText("Processing auth...");


        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}