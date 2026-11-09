package com.itsluminous.verysmarttv.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.itsluminous.verysmarttv.core.contract.ScoreContract;
import com.itsluminous.verysmarttv.testkit.FakeScoreProvider;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowContentResolver;

/**
 * The Backup document's companions section (DESIGN.md §12.2, ADR-032) against the companion stand-in: the export
 * carries the follow list the provider's /backup row returns, verbatim; restore hands it back through
 * call("restore"); without the companion the section is left out and a restore skips it.
 */
@RunWith(RobolectricTestRunner.class)
public class CompanionsTest {
    private static final String FOLLOWS = "{\"version\":1,\"follows\":[{\"kind\":\"competition\",\"id\":\"ipl\"}]}";
    private Context ctx;

    @Before
    public void aProvider() {
        ctx = RuntimeEnvironment.getApplication();
        FakeScoreProvider.reset();
        Robolectric.setupContentProvider(FakeScoreProvider.class, ScoreContract.AUTHORITY);
    }

    @After
    public void reset() {
        FakeScoreProvider.reset();
    }

    @Test
    public void theExportCarriesTheCompanionsBackupRowVerbatim() throws Exception {
        FakeScoreProvider.seedBackup(FOLLOWS);
        JSONObject doc = Backup.export(ctx);
        JSONObject companions = doc.getJSONObject(Companions.KEY);
        assertEquals(FOLLOWS, companions.getJSONObject(Companions.LIVESCORE).getString(ScoreContract.JSON));
    }

    @Test
    public void restoreHandsTheSectionBackThroughCall() throws Exception {
        JSONObject section = new JSONObject().put(Companions.LIVESCORE, new JSONObject().put(ScoreContract.JSON, FOLLOWS));
        assertTrue(Companions.restore(ctx, section));
        assertEquals(1, FakeScoreProvider.restored().size());
        assertEquals(FOLLOWS, FakeScoreProvider.restored().get(0));
    }

    @Test
    public void aFullRestorePutsTheFollowListBack() throws Exception {
        FakeScoreProvider.seedBackup(FOLLOWS);
        JSONObject doc = Backup.export(ctx);
        FakeScoreProvider.seedBackup("{}");
        Backup.restore(ctx, doc);
        assertEquals(FOLLOWS, FakeScoreProvider.restored().get(0));
    }

    @Test
    public void aDocumentWithoutTheSectionRestoresNothingToTheCompanion() throws Exception {
        JSONObject doc = Backup.export(ctx);
        doc.remove(Companions.KEY);
        Backup.restore(ctx, doc);
        assertTrue(FakeScoreProvider.restored().isEmpty());
        assertFalse(Companions.restore(ctx, null));
        assertFalse(Companions.restore(ctx, new JSONObject()));
    }

    @Test
    public void withoutTheCompanionTheSectionIsLeftOutAndSkipped() throws Exception {
        ShadowContentResolver.reset();
        JSONObject doc = Backup.export(ctx);
        assertNull("no provider: no section", doc.optJSONObject(Companions.KEY));
        assertNotNull("the launcher's own sections are still there", doc.getJSONObject("prefs"));
        JSONObject section = new JSONObject().put(Companions.LIVESCORE, new JSONObject().put(ScoreContract.JSON, FOLLOWS));
        assertFalse("a restore with a section but no companion skips it", Companions.restore(ctx, section));
        // And the full restore of a document that has one does not throw.
        doc.put(Companions.KEY, section);
        Backup.restore(ctx, doc);
    }
}
