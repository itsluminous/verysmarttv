package com.itsluminous.verysmarttv.topbar;

import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Feeds {@link ResourceBars} (DESIGN.md §9.1): RAM from {@code /proc/meminfo} ({@link MemInfo}) and storage from
 * {@code StatFs} on the data partition, read on a background thread every {@link #PERIOD_MS} while Home is resumed
 * ({@link #start}/{@link #stop} follow the controller's onResume/onPause), applied on the main thread. The readers are
 * injected, so {@code ResourceSamplerTest} feeds it fake lines and sizes; {@link #sample} is the pure part.
 */
public final class ResourceSampler {
    static final long PERIOD_MS = 5_000L;

    /** Where the figures come from; {@link #ANDROID} reads the TV, a test supplies its own. */
    public interface Readers {
        /** The lines of /proc/meminfo, or null when it cannot be read. */
        @Nullable
        List<String> memInfoLines();

        /** {free, total} bytes of the data partition, or null when it cannot be read. */
        @Nullable
        long[] storageBytes();
    }

    /** One reading: free and total bytes per row; a row whose reader failed is {@code known} false and stays hidden. */
    public static final class Sample {
        public final boolean ramKnown, storageKnown;
        public final long ramFree, ramTotal, storageFree, storageTotal;

        Sample(boolean ramKnown, long ramFree, long ramTotal, boolean storageKnown, long storageFree, long storageTotal) {
            this.ramKnown = ramKnown;
            this.ramFree = ramFree;
            this.ramTotal = ramTotal;
            this.storageKnown = storageKnown;
            this.storageFree = storageFree;
            this.storageTotal = storageTotal;
        }
    }

    public interface Listener {
        void onSample(@NonNull Sample sample);
    }

    /** The TV's own figures. */
    public static final Readers ANDROID = new Readers() {
        @Override
        @Nullable
        public List<String> memInfoLines() {
            List<String> lines = new ArrayList<>();
            try (BufferedReader in = new BufferedReader(new FileReader("/proc/meminfo"))) {
                String line;
                while ((line = in.readLine()) != null) lines.add(line);
            } catch (IOException | RuntimeException e) {
                return null;
            }
            return lines;
        }

        @Override
        @Nullable
        public long[] storageBytes() {
            try {
                StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
                return new long[]{fs.getAvailableBytes(), fs.getTotalBytes()};
            } catch (RuntimeException e) {
                return null;
            }
        }
    };

    private final Readers readers;
    private final Handler main;
    private final Listener listener;
    private boolean started;
    private volatile boolean reading;
    private final Runnable tick = this::read;

    public ResourceSampler(@NonNull Readers readers, @NonNull Handler main, @NonNull Listener listener) {
        this.readers = readers;
        this.main = main;
        this.listener = listener;
    }

    /** onResume: a reading now, then one every PERIOD_MS. */
    public void start() {
        if (started) return;
        started = true;
        main.removeCallbacks(tick);
        read();
    }

    /** onPause: nothing is read while Home is off screen. */
    public void stop() {
        started = false;
        main.removeCallbacks(tick);
    }

    private void read() {
        if (!started || reading) return;
        reading = true;
        new Thread(() -> {
            Sample s = sample(readers);
            main.post(() -> {
                reading = false;
                if (!started) return;
                listener.onSample(s);
                main.postDelayed(tick, PERIOD_MS);
            });
        }, "resource-sampler").start();
    }

    /** One reading from {@code readers}: the pure half, what the test drives. */
    @NonNull
    public static Sample sample(@NonNull Readers readers) {
        List<String> lines = readers.memInfoLines();
        long[] ram = lines == null ? null : MemInfo.usedOfTotalMb(lines);
        long[] storage = readers.storageBytes();
        boolean storageKnown = storage != null && storage.length == 2 && storage[1] > 0;
        long mb = 1024L * 1024L;
        return new Sample(ram != null, ram == null ? 0 : (ram[1] - ram[0]) * mb, ram == null ? 0 : ram[1] * mb,
                storageKnown, storageKnown ? Math.max(0, storage[0]) : 0, storageKnown ? storage[1] : 0);
    }
}
