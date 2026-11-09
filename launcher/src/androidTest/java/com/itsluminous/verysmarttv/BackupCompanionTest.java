package com.itsluminous.verysmarttv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.itsluminous.verysmarttv.backup.Backup;
import com.itsluminous.verysmarttv.backup.Companions;
import com.itsluminous.verysmarttv.core.contract.ScoreContract;
import com.itsluminous.verysmarttv.support.HomeTestRule;
import com.itsluminous.verysmarttv.support.ScoreStandIn;
import com.itsluminous.verysmarttv.testkit.ScoreFixture;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The companions section against the installed stand-in (DESIGN.md §14.3 BackupCompanionTest, ADR-032): the launcher's
 * export carries the stand-in's /backup JSON verbatim under companions.livescore, read through the signature
 * permission, and a restore hands a document's JSON back through call("restore"), which the stand-in records.
 */
@RunWith(AndroidJUnit4.class)
public class BackupCompanionTest {
    @Rule public final HomeTestRule home = new HomeTestRule();

    private Context ctx;

    @Before
    public void standInInstalled() {
        ctx = home.target();
        ScoreStandIn.assertInstalled(ctx);
    }

    @Test
    public void theExportCarriesTheCompanionsFollowList() throws Exception {
        JSONObject doc = Backup.export(ctx);
        JSONObject companions = doc.optJSONObject(Companions.KEY);
        assertNotNull("the companions section is there while the stand-in answers", companions);
        String json = companions.getJSONObject(Companions.LIVESCORE).getString(ScoreContract.JSON);
        // Either the fresh fixture or what an earlier restore left: both are the stand-in's own /backup row.
        assertTrue(json, json.equals(ScoreFixture.BACKUP_JSON) || json.contains("\"follows\""));
    }

    @Test
    public void restoreHandsTheFollowListBackToTheCompanion() throws Exception {
        JSONObject doc = Backup.export(ctx);
        String marker = "{\"version\":1,\"follows\":[{\"kind\":\"team\",\"id\":\"restored-" + System.nanoTime() + "\"}]}";
        doc.put(Companions.KEY, new JSONObject().put(Companions.LIVESCORE, new JSONObject().put(ScoreContract.JSON, marker)));
        Backup.restore(ctx, doc);
        assertEquals("the stand-in received exactly the document's JSON", marker, ScoreStandIn.lastRestored(ctx));
        // And it is now what the companion backs up.
        String after = Backup.export(ctx).getJSONObject(Companions.KEY).getJSONObject(Companions.LIVESCORE).getString(ScoreContract.JSON);
        assertEquals(marker, after);
    }
}
