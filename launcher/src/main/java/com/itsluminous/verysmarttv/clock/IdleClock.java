package com.itsluminous.verysmarttv.clock;

import android.content.Intent;
import android.view.View;

import androidx.annotation.NonNull;

import com.itsluminous.verysmarttv.home.HomeHost;
import com.itsluminous.verysmarttv.theme.Config;

/**
 * The clock Home opens by itself: after home_idle_clock_minutes without a key while Home is in front, and on Back
 * with back_opens_clock (DESIGN.md §8, moved from the activity verbatim). The countdown runs on the root view,
 * so it is dropped with the window.
 */
public final class IdleClock {
    private static final long MINUTE_MS = 60_000L;

    private final HomeHost host;
    private final View root;
    /** Idle time on Home before ClockActivity; 0 is off (config.xml, home_idle_clock_minutes). */
    private final long idleClockMs;
    /** Back on Home opens ClockActivity (config.xml, back_opens_clock). */
    private final boolean backOpensClock;
    private final Runnable showIdleClock;

    public IdleClock(@NonNull HomeHost host, @NonNull View root, @NonNull Config cfg) {
        this.host = host;
        this.root = root;
        idleClockMs = cfg.homeIdleClockMinutes * MINUTE_MS;
        backOpensClock = cfg.backOpensClock;
        showIdleClock = () -> host.activity().startActivity(new Intent(host.activity(), ClockActivity.class));
    }

    /** Any key on Home restarts the countdown to the idle clock (onUserInteraction, onResume). */
    public void restart() {
        root.removeCallbacks(showIdleClock);
        if (host.state().resumed && idleClockMs > 0) root.postDelayed(showIdleClock, idleClockMs);
    }

    /** onPause: nothing of ours opens over the app in front. */
    public void cancel() {
        root.removeCallbacks(showIdleClock);
    }

    /** Back on Home: goes nowhere, unless back_opens_clock is on. */
    public void onBackPressed() {
        if (backOpensClock) host.activity().startActivity(new Intent(host.activity(), ClockActivity.class));
    }
}
