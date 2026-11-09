package com.itsluminous.traktshelf;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

/** Device-code sign-in: show the code and the URL, poll until the user approves on trakt.tv, then sync. */
public final class SignInActivity extends Activity {
    private static final String TAG = "TraktShelf.SignIn";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView code, url, status, cancel;
    private Auth auth;
    private Auth.DeviceCode device;
    private Thread worker;
    private volatile boolean stopped;
    private boolean needsRetry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signin);
        Ui.applyFont(findViewById(android.R.id.content));
        code = findViewById(R.id.code);
        url = findViewById(R.id.url);
        status = findViewById(R.id.status);
        cancel = findViewById(R.id.btnCancel);
        auth = new Auth(this);
        cancel.setOnClickListener(v -> {
            if (needsRetry) start();
            else finish();
        });
        cancel.requestFocus();
        if (!Auth.configured()) {
            code.setText("—");
            status.setText(R.string.sign_in_no_key);
            return;
        }
        start();
    }

    private void start() {
        needsRetry = false;
        cancel.setText(R.string.cancel);
        code.setText(R.string.sign_in_code_placeholder);
        url.setText(R.string.sign_in_url);
        status.setText(R.string.sign_in_getting_code);
        stopped = false;
        worker = new Thread(() -> {
            try {
                device = auth.requestDeviceCode();
            } catch (Exception e) {
                Log.w(TAG, "device code: " + e.getMessage());
                handler.post(() -> fail(R.string.sign_in_error));
                return;
            }
            handler.post(() -> {
                code.setText(device.userCode);
                url.setText(device.verificationUrl.replace("https://", ""));
                status.setText(R.string.sign_in_waiting);
            });
            Log.i(TAG, "device code shown, expires in " + device.expiresIn + " s, polling every " + device.interval + " s");
            long deadline = System.currentTimeMillis() + device.expiresIn * 1000L;
            long interval = device.interval * 1000L;
            while (!stopped && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    return;
                }
                if (stopped) return;
                Auth.Poll p = auth.pollDeviceToken(device);
                switch (p) {
                    case AUTHORIZED:
                        Log.i(TAG, "authorized");
                        handler.post(this::done);
                        return;
                    case DENIED:
                        handler.post(() -> fail(R.string.sign_in_denied));
                        return;
                    case EXPIRED:
                    case ALREADY_USED:
                        handler.post(() -> fail(R.string.sign_in_expired));
                        return;
                    case SLOW_DOWN:
                        interval += 2000;
                        break;
                    default:
                        break;   // pending, or a transient error: poll again
                }
            }
            if (!stopped) handler.post(() -> fail(R.string.sign_in_expired));
        }, "trakt-device-poll");
        worker.start();
    }

    private void fail(int message) {
        status.setText(message);
        needsRetry = true;
        cancel.setText(R.string.retry);
    }

    private void done() {
        status.setText(R.string.sign_in_done);
        code.setVisibility(View.INVISIBLE);
        Sync.runNow(this, null);
        handler.postDelayed(this::finish, 1200);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopped = true;
        if (worker != null) worker.interrupt();
    }
}
