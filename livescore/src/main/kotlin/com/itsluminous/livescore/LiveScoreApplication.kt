package com.itsluminous.livescore

import android.app.Application
import android.os.Build
import android.webkit.WebView
import com.itsluminous.livescore.source.bwf.BwfCaptureService

/**
 * The companion's `Application` (ADR-050): its one job is the `:bwf` process, where the BWF page loader's WebView lives.
 * Android P and newer refuse a second process of one app that opens the default WebView data directory, so that process
 * names its own suffix before anything in it can create a WebView; the main process never creates one and does nothing
 * here. API 26 and 27 have neither the suffix API nor the lock.
 */
class LiveScoreApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && getProcessName().endsWith(BwfCaptureService.PROCESS_SUFFIX)) {
            WebView.setDataDirectorySuffix(BwfCaptureService.DATA_DIRECTORY_SUFFIX)
        }
    }
}
