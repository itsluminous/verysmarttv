package com.itsluminous.livescore

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Bundle
import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Discipline
import com.itsluminous.livescore.model.Follow
import com.itsluminous.livescore.model.FollowKind
import com.itsluminous.livescore.model.Player
import com.itsluminous.livescore.model.Side
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import com.itsluminous.verysmarttv.core.contract.ScoreContract
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import java.io.File
import java.time.ZoneId

/** `/pill` and `/events` columns, `getType`, backup/restore round trip, notify after a refresh, and the permission declarations. */
@RunWith(RobolectricTestRunner::class)
class ScoreProviderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val now = 1_794_236_400_000L
    private lateinit var source: FakeSource
    private lateinit var repo: ScoreRepo
    private lateinit var resolver: ContentResolver
    private var online = true

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        source = FakeSource()
        repo = ScoreRepo(app, source, FollowStore(File(folder.root, "f.json")), EventCache(File(folder.root, "e.json")), Prefs(app), { now }, ZoneId.of("Asia/Kolkata"), online = { online })
        ScoreRepo.set(repo)
        Robolectric.setupContentProvider(ScoreProvider::class.java, ScoreContract.AUTHORITY)
        resolver = app.contentResolver
        // In-process calls are checked against the app's own permissions: the launcher's WRITE_SCORES stands in.
        Shadows.shadowOf(app).grantPermissions(ScoreContract.PERMISSION_WRITE)
    }

    @After
    fun tearDown() {
        ScoreRepo.set(null)
    }

    @Test
    fun pillIsNoneWithAnEmptyCacheAndNeverBlocks() {
        resolver.query(ScoreContract.PILL, null, null, null, null)!!.use { c ->
            assertEquals(ScoreContract.PILL_COLUMNS.toList(), c.columnNames.toList())
            assertTrue(c.moveToFirst())
            assertEquals(ScoreContract.STATE_NONE, c.getString(c.getColumnIndexOrThrow(ScoreContract.STATE)))
            assertTrue(c.isNull(c.getColumnIndexOrThrow(ScoreContract.EVENT_ID)))
            assertEquals(1, c.count)
        }
        assertTrue(source.fetched.isEmpty()) // nothing live: a read fetches nothing
    }

    @Test
    fun pillRowComesFromTheCacheAndTheFormatter() {
        seedIplLive()
        resolver.query(ScoreContract.PILL, null, null, null, null)!!.use { c ->
            c.moveToFirst()
            assertEquals("live", c.getString(c.getColumnIndexOrThrow(ScoreContract.STATE)))
            assertEquals("LIVE", c.getString(c.getColumnIndexOrThrow(ScoreContract.KICKER)))
            assertEquals("T20 · CSK 145/3 (16.2) v MI", c.getString(c.getColumnIndexOrThrow(ScoreContract.LINE)))
            assertEquals("ipl1", c.getString(c.getColumnIndexOrThrow(ScoreContract.EVENT_ID)))
            assertEquals("cricket", c.getString(c.getColumnIndexOrThrow(ScoreContract.SPORT)))
            assertEquals("IPL", c.getString(c.getColumnIndexOrThrow(ScoreContract.COMPETITION)))
            assertEquals(now - 30_000L, c.getLong(c.getColumnIndexOrThrow(ScoreContract.UPDATED_MS)))
            assertEquals(0, c.getInt(c.getColumnIndexOrThrow(ScoreContract.STALE)))
            assertEquals("#E53935", c.getString(c.getColumnIndexOrThrow(ScoreContract.COLOR)))
        }
    }

    @Test
    fun pillHasOneRowPerEventThePillCanShowTheChosenFirst() {
        val upcoming = iplEvent().copy(
            id = "ipl2", state = State.UPCOMING, statusDetail = "Scheduled", startMs = now + 3_600_000L,
            home = Side("3", "Royal Challengers Bengaluru", "RCB", "Bengaluru"), away = Side("4", "Rajasthan Royals", "RR", "Rajasthan"),
        )
        repo.cache.merge(FetchKey(Sport.CRICKET, null), listOf(upcoming, iplEvent()), now - 30_000L, repo.window(now).first)
        resolver.query(ScoreContract.PILL, null, null, null, null)!!.use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals("ipl1", c.getString(c.getColumnIndexOrThrow(ScoreContract.EVENT_ID)))
            assertEquals("live", c.getString(c.getColumnIndexOrThrow(ScoreContract.STATE)))
            c.moveToNext()
            assertEquals("ipl2", c.getString(c.getColumnIndexOrThrow(ScoreContract.EVENT_ID)))
            assertEquals("upcoming", c.getString(c.getColumnIndexOrThrow(ScoreContract.STATE)))
            assertEquals("T20 \u00b7 RCB v RR", c.getString(c.getColumnIndexOrThrow(ScoreContract.LINE)))
        }
    }

    @Test
    fun eventsRowsCarryTheEighteenColumnsAndOneById() {
        seedIplLive()
        resolver.query(ScoreContract.EVENTS, null, null, null, null)!!.use { c ->
            assertEquals(ScoreContract.EVENT_COLUMNS.toList(), c.columnNames.toList())
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("Chennai Super Kings", c.getString(c.getColumnIndexOrThrow(ScoreContract.HOME_NAME)))
            assertEquals("145/3 (16.2/20 ov)", c.getString(c.getColumnIndexOrThrow(ScoreContract.HOME_SCORE)))
            assertEquals("competition", c.getString(c.getColumnIndexOrThrow(ScoreContract.FOLLOWED_REASON)))
            assertEquals("T20", c.getString(c.getColumnIndexOrThrow(ScoreContract.FORMAT)))
            assertTrue(c.isNull(c.getColumnIndexOrThrow(ScoreContract.END_MS)))
        }
        resolver.query(ScoreContract.event("ipl1"), null, null, null, null)!!.use { assertEquals(1, it.count) }
        resolver.query(ScoreContract.event("nope"), null, null, null, null)!!.use { assertEquals(0, it.count) }
        assertNull(resolver.query(ScoreContract.EVENTS.buildUpon().appendPath("x").appendPath("y").build(), null, null, null, null))
    }

    @Test
    fun typesFollowTheContract() {
        assertEquals(ScoreContract.TYPE_PILL, resolver.getType(ScoreContract.PILL))
        assertEquals(ScoreContract.TYPE_EVENTS, resolver.getType(ScoreContract.EVENTS))
        assertEquals(ScoreContract.TYPE_EVENT, resolver.getType(ScoreContract.event("1")))
        assertEquals(ScoreContract.TYPE_BACKUP, resolver.getType(ScoreContract.BACKUP))
    }

    @Test
    fun backupAndRestoreRoundTripThroughTheProvider() {
        repo.follows.toggle(Follow.competition(Competition(Sport.TENNIS, "atp", "ATP Tour")))
        val india = Follow.country("IND", "India", setOf(Discipline.MS, Discipline.WS))
        val chen = Follow.player(Player("57945", "CHEN Yu Fei", "CHN"), Discipline.ALL)
        repo.follows.replaceAll(emptyList(), listOf(india, chen))
        val json = resolver.query(ScoreContract.BACKUP, null, null, null, null)!!.use { c ->
            c.moveToFirst()
            c.getString(c.getColumnIndexOrThrow(ScoreContract.JSON))
        }
        assertEquals(repo.follows.export(), json)
        repo.follows.clear()
        val ok = resolver.call(ScoreContract.AUTHORITY, ScoreContract.METHOD_RESTORE, null, Bundle().apply { putString(ScoreContract.EXTRA_JSON, json) })!!
        assertTrue(ok.getBoolean(ScoreContract.RESULT_OK))
        assertEquals(9, repo.follows.all().size)
        assertEquals(india, repo.follows.all().first { it.kind == FollowKind.COUNTRY })
        assertEquals(chen, repo.follows.all().first { it.kind == FollowKind.PLAYER })
        val bad = resolver.call(ScoreContract.AUTHORITY, ScoreContract.METHOD_RESTORE, null, Bundle().apply { putString(ScoreContract.EXTRA_JSON, "nope") })!!
        assertFalse(bad.getBoolean(ScoreContract.RESULT_OK))
        assertEquals("not a follow list", bad.getString(ScoreContract.RESULT_ERROR))
        assertFalse(resolver.call(ScoreContract.AUTHORITY, "other", null, null)!!.getBoolean(ScoreContract.RESULT_OK))
        assertFalse(resolver.call(ScoreContract.AUTHORITY, ScoreContract.METHOD_RESTORE, null, null)!!.getBoolean(ScoreContract.RESULT_OK))
    }

    @Test
    fun aReadWhileLiveFetchesOncePerCompetitionAndNotifies() {
        seedIplLive(fetchedAgoMs = 60_000L)
        val notified = ArrayList<String>()
        resolver.registerContentObserver(ScoreContract.PILL, false, object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                notified.add("pill")
            }
        })
        source.events[Sport.CRICKET] = mutableListOf(iplEvent().copy(summary = "fresh"))
        resolver.query(ScoreContract.PILL, null, null, null, null)!!.close()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        waitForFetch()
        assertEquals(listOf(Sport.CRICKET to null), source.fetched)
        assertEquals("fresh", repo.event("ipl1")!!.summary)
        // The cache is written before the fetch thread notifies: wait for the notification as well as the event.
        val deadline = System.currentTimeMillis() + 5_000
        while (notified.isEmpty() && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertTrue(Shadows.shadowOf(resolver).notifiedUris.any { it.uri == ScoreContract.PILL })
        assertTrue(notified.isNotEmpty())
        // A second read right after: the cache is fresh, nothing fetched.
        resolver.query(ScoreContract.PILL, null, null, null, null)!!.close()
        waitForFetch()
        assertEquals(1, source.fetched.size)
    }

    @Test
    fun offlineReadsServeTheCacheAndFetchNothing() {
        seedIplLive(fetchedAgoMs = 600_000L)
        online = false
        resolver.query(ScoreContract.PILL, null, null, null, null)!!.use { c ->
            c.moveToFirst()
            assertEquals("live", c.getString(c.getColumnIndexOrThrow(ScoreContract.STATE)))
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow(ScoreContract.STALE)))
        }
        Thread.sleep(100)
        assertTrue(source.fetched.isEmpty())
    }

    @Test
    fun aBackgroundRefreshLeavesTheBwfWebViewSourceOutAndFetchesTheRest() {
        repo.follows.toggle(Follow.sport(Sport.BADMINTON, "Badminton"))
        repo.follows.toggle(Follow.sport(Sport.CRICKET, "Cricket"))
        repo.prefs.badmintonSource = BadmintonSource.BWF_WEBVIEW
        refreshAndWait(background = true)
        assertFalse(source.fetched.any { it.first == Sport.BADMINTON }) // no hidden browser off screen (ADR-049)
        assertTrue(source.fetched.any { it.first == Sport.CRICKET }) // the rest of the plan is untouched
        source.fetched.clear()
        refreshAndWait(background = false) // a screen asked: the WebView source runs
        assertTrue(source.fetched.any { it.first == Sport.BADMINTON })
        source.fetched.clear()
        repo.prefs.badmintonSource = BadmintonSource.SPORTMICRO
        refreshAndWait(background = true) // the keyed source is a plain HTTP call: the job fetches it as before
        assertTrue(source.fetched.any { it.first == Sport.BADMINTON })
    }

    private fun refreshAndWait(background: Boolean) {
        var done = false
        if (background) repo.refreshInBackground { done = true } else repo.refreshAll { done = true }
        val deadline = System.currentTimeMillis() + 5_000
        while (!done && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertTrue(done)
    }

    @Test
    fun followChangesNotifyThePill() {
        Shadows.shadowOf(resolver).notifiedUris.clear()
        repo.follows.toggle(Follow.sport(Sport.TENNIS, "Tennis"))
        assertTrue(Shadows.shadowOf(resolver).notifiedUris.any { it.uri == ScoreContract.PILL })
    }

    @Test
    fun restoreIsRefusedWithoutTheWritePermission() {
        // The framework checks no permission on call() itself (§12.2): the provider must.
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(ScoreContract.PERMISSION_WRITE)
        val before = repo.follows.all().size
        try {
            resolver.call(ScoreContract.AUTHORITY, ScoreContract.METHOD_RESTORE, null, Bundle().apply { putString(ScoreContract.EXTRA_JSON, "[]") })
            fail("call(restore) without WRITE_SCORES must throw")
        } catch (expected: SecurityException) {
            assertTrue(expected.message!!, expected.message!!.contains(ScoreContract.PERMISSION_WRITE))
        }
        assertEquals("nothing was replaced", before, repo.follows.all().size)
        // Reads need only READ_SCORES, which the framework enforces before the query reaches the provider.
        resolver.query(ScoreContract.PILL, null, null, null, null)!!.use { assertEquals(1, it.count) }
    }

    @Test
    fun providerIsGuardedByTheTwoSignaturePermissions() {
        val pm = RuntimeEnvironment.getApplication().packageManager
        val info = pm.resolveContentProvider(ScoreContract.AUTHORITY, 0)!!
        assertEquals(ScoreContract.PERMISSION_READ, info.readPermission)
        assertEquals(ScoreContract.PERMISSION_WRITE, info.writePermission)
        assertTrue(info.exported)
        for (name in listOf(ScoreContract.PERMISSION_READ, ScoreContract.PERMISSION_WRITE)) {
            val permission = pm.getPermissionInfo(name, 0)
            assertEquals(name, android.content.pm.PermissionInfo.PROTECTION_SIGNATURE, permission.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE)
        }
    }

    private fun iplEvent(): Event =
        Event(
            "ipl1", Sport.CRICKET, "8048", "IPL", "T20", State.LIVE, "Live", now - 3_600_000L, null,
            Side("335974", "Chennai Super Kings", "CSK", "Chennai", "145/3 (16.2/20 ov)", listOf(com.itsluminous.livescore.model.Innings(145, 3, 16.2, isCurrent = true, isBatting = true)), isBatting = true),
            Side("335978", "Mumbai Indians", "MI", "Mumbai"), "", now - 30_000L,
        )

    private fun seedIplLive(fetchedAgoMs: Long = 30_000L) {
        repo.cache.merge(FetchKey(Sport.CRICKET, null), listOf(iplEvent().copy(updatedMs = now - fetchedAgoMs)), now - fetchedAgoMs, repo.window(now).first)
    }

    private fun waitForFetch() {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            if (source.fetched.isNotEmpty() && repo.event("ipl1")?.summary == "fresh") return
            Thread.sleep(20)
        }
    }
}
