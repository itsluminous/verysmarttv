package com.itsluminous.livescore

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import com.itsluminous.livescore.source.ScoreSource
import com.itsluminous.livescore.source.Sources
import com.itsluminous.verysmarttv.core.contract.ScoreContract
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The one object behind the provider, the UI and the job (§13.5, ADR-014): the follow list, the event
 * cache, the source, and the refresh decisions. Everything network runs on one single-thread executor;
 * reads answer from the cache at once. `notifyChange` on the provider URIs after every successful fetch
 * and every follow change is how the launcher learns of a change.
 */
class ScoreRepo(
    context: Context,
    val source: ScoreSource,
    val follows: FollowStore,
    val cache: EventCache,
    val prefs: Prefs,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val online: () -> Boolean = { connected(context.applicationContext) },
) {
    private val app = context.applicationContext
    private val policy = RefreshPolicy()
    val formatter = LineFormatter(zone)
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "LiveScore.fetch") }
    private val handler = Handler(Looper.getMainLooper())
    private val fetchRunning = AtomicBoolean()

    @Volatile private var failures = 0

    @Volatile private var lastFailureMs = 0L

    @Volatile private var lastReadMs = 0L
    private val listeners = ArrayList<() -> Unit>()

    init {
        follows.addListener { onFollowsChanged() }
    }

    /** `yesterday 00:00` to `tomorrow 23:59:59` local (§12.2). */
    fun window(nowMs: Long): LongRange {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val start = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start..end
    }

    /** The followed events in the window, shown sports only, live first then by start. */
    fun followed(nowMs: Long = clock()): List<Followed> {
        follows.prune(nowMs)
        val window = window(nowMs)
        val shown = prefs.sportsShown
        val events = cache.events().filter { it.sport in shown && inWindow(it, window) }
        return Follows.match(events, follows.all()).sortedWith(compareBy<Followed> { if (it.event.isLiveOrBreak) 0 else 1 }.thenBy { it.event.startMs })
    }

    private fun inWindow(e: Event, window: LongRange): Boolean =
        e.startMs in window || (e.endMs ?: e.startMs) in window || e.startMs < window.first && e.state != State.FINAL

    /** Every event the pill can show, the pill's own (PillChooser.choose) first; empty for `state=none`. */
    fun pillRows(nowMs: Long = clock()): List<Followed> = PillChooser.ranked(followed(nowMs), nowMs)

    fun event(id: String): Event? = cache.event(id)

    fun isStale(event: Event, nowMs: Long = clock()): Boolean = policy.isStale(event, nowMs)

    /** A screen read the provider: fetch what §13.5 says is due, and keep the 20 s alternation going while reads continue. */
    fun onRead() {
        val now = clock()
        lastReadMs = now
        if (!prefs.liveRefresh) return
        val followed = followed(now)
        val keys = policy.onRead(followed, cache.fetchedMs(), online(), fetchRunning.get(), failures, lastFailureMs, now)
        if (keys.isNotEmpty()) fetch(keys, liveOnly = true, onDone = null)
        if (PillChooser.alternates(followed, now)) scheduleAlternation()
    }

    /** Fetches every followed competition (the job, Settings, the UI on open); [onDone] runs on the main thread. */
    fun refreshAll(onDone: (() -> Unit)?) {
        fetch(RefreshPolicy.fullPlan(follows.all().filter { it.sport in prefs.sportsShown }, source), liveOnly = false, onDone = onDone)
    }

    /**
     * One day-wide sport, fetched whole (the picker, when a sport without a static catalogue has nothing cached to
     * offer yet); [onDone] runs on the main thread. Nothing for a sport whose feed is per competition.
     */
    fun refreshSport(sport: Sport, onDone: (() -> Unit)?) {
        if (!sport.oneFetchPerDay) {
            if (onDone != null) handler.post(onDone)
            return
        }
        fetch(listOf(FetchKey(sport, null)), liveOnly = false, onDone = onDone)
    }

    /** The open scoreboard's 30 s refresh: the event's competition, then its fuller view; [onDone] on the main thread. */
    fun refreshOne(event: Event, onDone: (Event?) -> Unit) {
        fetch(listOf(FetchKey.of(event)), liveOnly = true) {
            val fresh = cache.event(event.id)
            if (fresh == null) onDone(null) else fetchDetail(fresh) { onDone(it ?: fresh) }
        }
    }

    /** When the cache was last written by any fetch, 0 when never. */
    fun lastFetchMs(): Long = cache.fetchedMs().values.maxOrNull() ?: 0L

    /** Fetches the fuller view of one event for the scoreboard; [onDone] gets the event on the main thread, null on failure. */
    fun fetchDetail(event: Event, onDone: (Event?) -> Unit) {
        executor.execute {
            val result = try {
                source.event(event)?.also { cache.replace(it) }
            } catch (e: IOException) {
                Log.w(TAG, "detail ${event.id}: ${e.message}")
                null
            }
            handler.post { onDone(result) }
        }
    }

    private fun fetch(keys: List<FetchKey>, liveOnly: Boolean, onDone: (() -> Unit)?) {
        if (keys.isEmpty() || !fetchRunning.compareAndSet(false, true)) {
            if (onDone != null) handler.post(onDone)
            return
        }
        executor.execute {
            var failed = false
            var rows = 0
            val now = clock()
            for (key in keys) {
                try {
                    val events = fetchKey(key, liveOnly, now)
                    cache.merge(key, events, clock(), window(now).first)
                    rows += events.size
                } catch (e: IOException) {
                    failed = true
                    Log.w(TAG, "fetch ${key.token}: ${e.message}")
                } catch (e: IllegalStateException) {
                    failed = true
                    Log.w(TAG, "fetch ${key.token}: feed shape changed: ${e.message}")
                }
            }
            if (failed) {
                failures++
                lastFailureMs = clock()
            } else {
                failures = 0
            }
            if (Log.isLoggable(TAG, Log.INFO)) {
                Log.i(TAG, "refresh ${if (liveOnly) "live" else "full"}: ${keys.size} fetch(es), $rows events, ${if (failed) "with failures, backoff ${policy.backoffMs(failures) / 60_000} min" else "ok"}")
            }
            fetchRunning.set(false)
            notifyChanged()
            scheduleJobs()
            if (onDone != null) handler.post(onDone)
        }
    }

    /** The day expressions one key needs: today for cricket; today (and tomorrow on a full refresh) for football and badminton, one request per day; the window for F1 and tennis. */
    @Throws(IOException::class)
    private fun fetchKey(key: FetchKey, liveOnly: Boolean, nowMs: Long): List<Event> {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val day = { d: Int -> today.plusDays(d.toLong()).format(DAY) }
        val competition = key.competitionId?.let { id -> source.competitions(key.sport).firstOrNull { it.id == id } }
        val days = when (key.sport) {
            Sport.CRICKET -> listOf(day(0))
            Sport.FOOTBALL -> if (liveOnly) listOf(day(0)) else listOf(day(0), day(1))
            Sport.BADMINTON -> if (liveOnly) listOf(day(0)) else listOf(day(0), day(1))
            Sport.F1, Sport.TENNIS -> listOf("${day(-1)}-${day(1)}")
        }
        return days.flatMap { source.scoreboard(key.sport, competition, it) }.distinctBy { it.id }
    }

    private fun onFollowsChanged() {
        notifyChanged()
        scheduleJobs()
    }

    private fun notifyChanged() {
        val resolver = app.contentResolver
        resolver.notifyChange(ScoreContract.PILL, null)
        resolver.notifyChange(ScoreContract.EVENTS, null)
        handler.post {
            val copy = synchronized(listeners) { listeners.toList() }
            copy.forEach { it() }
        }
    }

    /** In-process listeners (the activities), called on the main thread after every change. */
    fun addListener(listener: () -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: () -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    private val alternation = object : Runnable {
        override fun run() {
            val now = clock()
            // Keep turning the pill only while something read it in the last 90 s; the launcher re-reads on notify.
            if (now - lastReadMs <= ALTERNATION_KEEPALIVE_MS && PillChooser.alternates(followed(now), now)) {
                app.contentResolver.notifyChange(ScoreContract.PILL, null)
                handler.postDelayed(this, PillChooser.ALTERNATE_MS)
            }
        }
    }

    private fun scheduleAlternation() {
        handler.removeCallbacks(alternation)
        handler.postDelayed(alternation, PillChooser.ALTERNATE_MS)
    }

    /** The clock every row is stamped against; tests inject a fixed one. */
    fun now(): Long = clock()

    /** The periodic 6 h fixture job (once) and the one-off job before the next followed start (§13.5). */
    fun scheduleJobs() {
        val js = app.getSystemService(JobScheduler::class.java) ?: return
        val component = ComponentName(app, RefreshJob::class.java)
        try {
            if (js.getPendingJob(JOB_PERIODIC) == null) {
                js.schedule(
                    JobInfo.Builder(JOB_PERIODIC, component)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPeriodic(RefreshPolicy.PERIODIC_MS)
                        .build(),
                )
            }
            val now = clock()
            val next = policy.nextOneOffMs(followed(now), now)
            if (next == null) {
                js.cancel(JOB_ONE_OFF)
            } else {
                js.schedule(
                    JobInfo.Builder(JOB_ONE_OFF, component)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setMinimumLatency(next - now)
                        .setOverrideDeadline(next - now + RefreshPolicy.START_LAG_MS)
                        .build(),
                )
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "jobs not scheduled: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "LiveScore.Repo"
        const val JOB_PERIODIC = 1
        const val JOB_ONE_OFF = 2
        private const val ALTERNATION_KEEPALIVE_MS = 90_000L
        private val DAY = DateTimeFormatter.ofPattern("yyyyMMdd")

        // The one repo holds the application context, never an activity's: the leak lint fears cannot happen.
        @Suppress("StaticFieldLeak")
        @Volatile private var instance: ScoreRepo? = null

        /** The process's repo, built on first use from the registered sources and the app's files dir. */
        fun get(context: Context): ScoreRepo =
            instance ?: synchronized(this) {
                instance ?: create(context).also { instance = it }
            }

        /** Replaces the process's repo; tests install one with a scripted source before touching the provider. */
        fun set(repo: ScoreRepo?) {
            instance = repo
        }

        /** Whether the TV has a network with internet access right now (§13.5: offline, nothing is fetched). */
        fun connected(app: Context): Boolean {
            val cm = app.getSystemService(ConnectivityManager::class.java) ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        private fun create(context: Context): ScoreRepo {
            val app = context.applicationContext
            val prefs = Prefs(app)
            return ScoreRepo(
                app,
                Sources.create(app, prefs),
                FollowStore(File(app.filesDir, FollowStore.FILE_NAME)),
                EventCache(File(app.filesDir, EventCache.FILE_NAME)),
                prefs,
            )
        }
    }
}
