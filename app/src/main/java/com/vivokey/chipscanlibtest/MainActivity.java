package com.vivokey.chipscanlibtest;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
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
    Button start;
    ReaderDiscovery tagCallback;
    Thread getChallAuth;
    boolean started = false;



    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /**
         * Fired when the Activity is created. We set up the GUI and grab a new Authenticator object.
         */
        Bundle nfcExtras = new Bundle();
        nfcExtras.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 5000);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if(tagCallback == null) {
            tagCallback = new ReaderDiscovery();
        }
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        // You need to put your own API key here. This one is likely to be disabled.
        auth = new VivoAuthenticator("");
        running = findViewById(R.id.progressBar);
        mainText = findViewById(R.id.textView);
        tv3 = findViewById(R.id.textView3);
        start = findViewById(R.id.button);
        mHandler = new Handler();
        mainText.setText("Waiting for tag...");
        // Starts a challenge checker
        if(getChallAuth == null) {
            getChallAuth = new Thread(() -> {
                auth.getChallenge();
            });
        }
        // Check for internet
        ConnectivityManager cm =
                (ConnectivityManager)this.getSystemService(Context.CONNECTIVITY_SERVICE);
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
        if(!isConnected) {
            System.out.println("No internet.");
            // Shut the app down.
            this.finishAffinity();
        }

        //getChallAuth.start();
    }

    /**
     * Called when the start button is pressed
     * @param view
     */
    public void toggleScan(View view) {

        if(!started) {
            if(tagCallback == null) {
                tagCallback = new ReaderDiscovery();
            }
            started = true;
            Bundle nfcExtras = new Bundle();
            nfcExtras.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 5000);
            mNfcAdapter.enableReaderMode(this, tagCallback, NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_NFC_V|NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, nfcExtras);
            start.setText("Stop Scan");
        } else {
            started = false;
            mNfcAdapter.disableReaderMode(this);
            getChallAuth.interrupt();
            start.setText("Start Scan");
        }
    }

    @Override
    protected void onResume() {
        /**
         * After we get resumed from a pause. Just sees if we have a pending intent, resets the
         * adapter and re-enables dispatch.
         */
        super.onResume();


        if(getChallAuth == null) {
            getChallAuth = new Thread(() -> {
                auth.getChallenge();
            });
        }
        if(!getChallAuth.isAlive()) {
            getChallAuth.start();
        }

    }

    @Override
    protected void onPause() {
        /**
         * Runs before we pause. Just disables the reader mode.
         */
        super.onPause();
        if(started) {
            started = false;
            mNfcAdapter.disableReaderMode(this);
            getChallAuth.interrupt();
            start.setText("Start Scan");
        }

    }

    /**
     * This is the polling service. It uses the handler to schedule itself to run every .1 seconds.
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
                mHandler.postDelayed(mStatusChecker, 100);
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
            started = false;
            // Ignore the tag so we don't get a NTAG read immediately after we stop the reading
            mNfcAdapter.ignore(vivotag.getTag(), 1000, null, null);
            mNfcAdapter.disableReaderMode(this);
            getChallAuth.interrupt();
            start.setText("Start Scan");

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


    /**
     * So this handles the discovery of tags, it's a different way of doing it but may fix problems.
     */
    class ReaderDiscovery implements NfcAdapter.ReaderCallback {

        @Override
        public void onTagDiscovered(Tag tag) {
            try {
                if(!auth.isRunning()) {
                    vivotag = new VivoTag(tag);
                    auth.setTag(vivotag);
                    auth.start();
                    startRepeatingTask();
                    running.setVisibility(View.VISIBLE);
                    mainText.setText("Processing auth...");
                }

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}