package com.itsluminous.traktshelf;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** The six-hourly sync, run by JobScheduler when the TV is online. */
public final class SyncJob extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        Sync.runNow(this, () -> jobFinished(params, false));
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;   // the sync thread finishes on its own; ask for a reschedule
    }
}
