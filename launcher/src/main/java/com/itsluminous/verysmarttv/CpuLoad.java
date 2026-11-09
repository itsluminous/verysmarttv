package com.itsluminous.verysmarttv;

/**
 * widgets: the CPU gauge's figure where the TV hides /proc/stat from apps (Android 10 does) and has no cpuidle
 * counters either, so busy time cannot be read. What sysfs still lets an app read is each core's clock, and a
 * governor such as ondemand raises the clock with the load: the gauge shows how far the cores are clocked above
 * idle, {@code (cur - floor) / (top - floor)} per core, averaged over the cores that answer. The floor is the
 * smallest entry of {@code scaling_available_frequencies} when that table can be read, and {@code cpuinfo_min_freq}
 * otherwise: the Mi TV 4A's table is 1150, 1350 and 1500 MHz while its cpuinfo_min_freq says 216 MHz, a clock the
 * governor never picks, so idle cores sit at 1150 MHz and a plain share of the top clock could never drop below 77%.
 * With a floor of 1150 MHz the same TV reads 0% at idle and 100% at full clock.
 */
final class CpuLoad {

    private CpuLoad() {
    }

    /**
     * The load percent for one reading: per core {@code (cur - floor) / (top - floor)}, clamped to 0..1, the mean
     * over the cores, as a percent 0..100. A core whose floor is not below its top counts as 0 (a fixed clock says
     * nothing). -1 when no core gave a reading (the arrays are empty or null).
     */
    static int percent(long[] cur, long[] floor, long[] top) {
        if (cur == null || floor == null || top == null || cur.length == 0) return -1;
        int cores = Math.min(cur.length, Math.min(floor.length, top.length));
        if (cores == 0) return -1;
        double sum = 0;
        for (int i = 0; i < cores; i++) {
            long span = top[i] - floor[i];
            if (span <= 0) continue;
            double share = (cur[i] - floor[i]) / (double) span;
            sum += Math.max(0, Math.min(1, share));
        }
        return (int) Math.round(sum * 100.0 / cores);
    }

    /**
     * The floor a core's load is measured from: the smallest frequency in its scaling_available_frequencies
     * (space separated, any order) when that line parses to at least one value below the top, else its
     * cpuinfo_min_freq; -1 when neither gives a value below the top.
     */
    static long floor(String availableFrequencies, long cpuinfoMinFreq, long top) {
        long smallest = -1;
        if (availableFrequencies != null) {
            for (String part : availableFrequencies.trim().split("\\s+")) {
                if (part.isEmpty()) continue;
                try {
                    long f = Long.parseLong(part);
                    if (f > 0 && f < top && (smallest < 0 || f < smallest)) smallest = f;
                } catch (NumberFormatException ignored) {
                    // a word in the table: skip it, the rest may still parse
                }
            }
        }
        if (smallest > 0) return smallest;
        return cpuinfoMinFreq > 0 && cpuinfoMinFreq < top ? cpuinfoMinFreq : -1;
    }

    /**
     * scaling_cur_freq moves in a few coarse steps (three on the Mi TV 4A), so one reading would jump the gauge
     * 0, 57, 100; an exponential moving average with a span of about three readings (alpha 1/2) lets it glide.
     * The first reading seeds it, so the gauge shows a figure as soon as one is in.
     */
    static final class Smoother {
        static final float ALPHA = 0.5f;
        private float ema = -1;

        /** The smoothed percent for one raw reading; a negative raw reading (nothing read) resets and passes through. */
        int next(int raw) {
            if (raw < 0) {
                ema = -1;
                return -1;
            }
            ema = ema < 0 ? raw : ema + ALPHA * (raw - ema);
            return Math.round(ema);
        }

        void reset() {
            ema = -1;
        }
    }
}
