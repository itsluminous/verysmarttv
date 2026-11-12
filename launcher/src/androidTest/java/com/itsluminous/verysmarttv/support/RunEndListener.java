package com.itsluminous.verysmarttv.support;

import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;

import java.io.IOException;

/**
 * Runs once after the last test of an instrumentation run (the runner's {@code listener} argument, set in
 * {@code launcher/build.gradle.kts}) and puts back what a test left changed for the whole run: the stock launcher
 * {@link StockLauncher} disabled (ADR-052).
 */
public final class RunEndListener extends RunListener {
    @Override
    public void testRunFinished(Result result) throws IOException {
        StockLauncher.enableAgain();
    }
}
