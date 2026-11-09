package com.itsluminous.verysmarttv.support;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Records every activity the launcher starts while it is installed, and stops the start: the framework's
 * {@link Instrumentation.ActivityMonitor} built with its no-argument constructor sees each {@code startActivity} of
 * the instrumented process in {@link #onStartActivity}, and a non-null result there blocks the launch. So a test can
 * check the intent OK on a carousel item builds (its component and extras) without the companion's details screen
 * ever opening over Home. Nothing from AndroidX is needed for this. Call {@link #stop()} in {@code @After}.
 */
public final class IntentCapture extends Instrumentation.ActivityMonitor {
    private final List<Intent> seen = new ArrayList<>();

    private IntentCapture() {
        super();
    }

    /** Installs a capture on the instrumentation; every start from now on is recorded and blocked. */
    public static IntentCapture start() {
        IntentCapture capture = new IntentCapture();
        InstrumentationRegistry.getInstrumentation().addMonitor(capture);
        return capture;
    }

    @Override
    public Instrumentation.ActivityResult onStartActivity(Intent intent) {
        synchronized (seen) {
            seen.add(new Intent(intent));
        }
        return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null);
    }

    /** The intents recorded so far, oldest first. */
    public List<Intent> intents() {
        synchronized (seen) {
            return new ArrayList<>(seen);
        }
    }

    /** Removes the capture; starts go through again. */
    public void stop() {
        InstrumentationRegistry.getInstrumentation().removeMonitor(this);
    }
}
