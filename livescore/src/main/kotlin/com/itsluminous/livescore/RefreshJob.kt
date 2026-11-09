package com.itsluminous.livescore

import android.app.job.JobParameters
import android.app.job.JobService

/** The 6 h fixture refresh and the one-off job before a followed start, run by JobScheduler when online (§13.5). */
class RefreshJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        ScoreRepo.get(this).refreshAll { jobFinished(params, false) }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
