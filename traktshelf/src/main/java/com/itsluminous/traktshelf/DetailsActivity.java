package com.itsluminous.traktshelf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.itsluminous.verysmarttv.core.contract.HeroContract;

import org.json.JSONException;

/**
 * One title: art, synopsis, ratings, cast, and Play / Trailer / Watchlist / Mark watched. Opened from Home with the
 * item itself, or from the launcher with extras trakt_id and kind (the item is then looked up in the feed).
 */
public final class DetailsActivity extends Activity {
    static final String EXTRA_TRAKT_ID = HeroContract.EXTRA_TRAKT_ID, EXTRA_KIND = HeroContract.EXTRA_KIND, EXTRA_ITEM = "item";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Item item;
    private boolean onWatchlist, writing;
    private TextView btnWatchlist, btnWatched;

    static Intent intent(Context c, Item it) {
        Intent i = new Intent(c, DetailsActivity.class).putExtra(EXTRA_TRAKT_ID, it.traktId).putExtra(EXTRA_KIND, it.kind);
        try {
            i.putExtra(EXTRA_ITEM, it.toJson().toString());
        } catch (JSONException ignored) {
        }
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);
        Ui.applyFont(findViewById(android.R.id.content));
        long traktId = getIntent().getLongExtra(EXTRA_TRAKT_ID, 0);
        String kind = getIntent().getStringExtra(EXTRA_KIND);
        Feed feed = Feed.load(this);
        if (traktId > 0 && kind != null) item = feed.find(kind, traktId);
        if (item == null && getIntent().hasExtra(EXTRA_ITEM)) {
            try {
                item = Item.fromJson(new org.json.JSONObject(getIntent().getStringExtra(EXTRA_ITEM)));
            } catch (JSONException ignored) {
            }
        }
        if (item == null) {
            Toast.makeText(this, R.string.failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        onWatchlist = feed.onWatchlist(item);

        TextView tag = findViewById(R.id.tag);
        tag.setText(item.nextEpisode != null ? getString(R.string.row_up_next) + " · " + item.nextEpisode
                : getString(Item.SHOW.equals(item.kind) ? R.string.show : R.string.movie) + (item.status != null ? " · " + item.status : "")
                + (item.certification != null ? " · " + item.certification : ""));
        TextView title = findViewById(R.id.title);
        Ui.fitOneLine(title, 46f, 28f);
        title.setText(item.title);
        ((TextView) findViewById(R.id.meta)).setText(item.metaLine());
        ((TextView) findViewById(R.id.synopsis)).setText(item.overview);
        TextView tagline = findViewById(R.id.tagline);
        if (item.tagline != null && !item.tagline.isEmpty()) {
            tagline.setText(item.tagline);
            tagline.setVisibility(View.VISIBLE);
        }
        TextView castLine = findViewById(R.id.castLine);
        castLine.setText(item.cast.isEmpty() ? "" : getString(R.string.cast) + ": " + String.join(", ", item.cast));
        LinearLayout chips = findViewById(R.id.chips);
        for (TextView chip : Ui.ratingChips(this, item)) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = (int) Ui.dp(this, 8);
            chips.addView(chip, lp);
        }
        ImageView backdrop = findViewById(R.id.backdrop);
        final String file = item.backdropFile();
        final int w = (int) Ui.dp(this, 640), h = (int) Ui.dp(this, 360);
        PosterAdapter.DECODER.execute(() -> {
            Bitmap b = Images.decode(this, file, w, h);
            if (b != null) handler.post(() -> backdrop.setImageBitmap(b));
        });

        View play = findViewById(R.id.btnPlay);
        play.setOnClickListener(v -> Players.play(this, item));
        findViewById(R.id.btnTrailer).setOnClickListener(v -> Players.trailer(this, item));
        btnWatchlist = findViewById(R.id.btnWatchlist);
        btnWatchlist.setText(onWatchlist ? R.string.remove_watchlist : R.string.add_watchlist);
        btnWatchlist.setOnClickListener(v -> write(onWatchlist ? "remove" : "add"));
        btnWatched = findViewById(R.id.btnWatched);
        btnWatched.setText(markWatchedLabel());
        btnWatched.setOnClickListener(v -> write("watched"));
        play.requestFocus();
    }

    // TizenTube's cold start drops the trailer link; Players re-sends it, telling cold from warm by these three.
    @Override
    protected void onStart() {
        super.onStart();
        Players.ColdStartResend.cancel();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Players.ColdStartResend.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Players.ColdStartResend.onStop(this);
    }

    private String markWatchedLabel() {
        return item.nextEpisode != null ? getString(R.string.mark_episode_watched, item.nextEpisode.split(" · ")[0]) : getString(R.string.mark_watched);
    }

    /**
     * One Trakt write, off the main thread. The pill says so at once (Adding…, Removing…, Marking…, dimmed) and takes
     * no second press; it keeps focus, which setEnabled(false) would hand to its neighbour. When Trakt answers it reads the new state (Remove from watchlist, Watched ✓), or its old label
     * again with the reason in a toast. A watchlist change goes into feed.json and to the provider's observers
     * right away, so Home's row and the launcher's shelf follow before the next sync; the sync then confirms it.
     */
    private void write(String what) {
        if (writing) return;
        Auth auth = new Auth(this);
        if (!auth.signedIn()) {
            Toast.makeText(this, R.string.needs_sign_in, Toast.LENGTH_SHORT).show();
            return;
        }
        final boolean watchlist = !"watched".equals(what);
        final TextView pill = watchlist ? btnWatchlist : btnWatched;
        final CharSequence before = pill.getText();
        writing = true;
        pill.setAlpha(0.55f);
        pill.setText("add".equals(what) ? R.string.adding_watchlist : "remove".equals(what) ? R.string.removing_watchlist : R.string.marking_watched);
        Trakt trakt = new Trakt(auth);
        new Thread(() -> {
            String error;
            switch (what) {
                case "add":
                    error = trakt.addToWatchlist(item);
                    break;
                case "remove":
                    error = trakt.removeFromWatchlist(item);
                    break;
                default:
                    error = trakt.markWatched(item);
            }
            if (error == null && watchlist) {
                Feed feed = Feed.load(this);
                feed.setOnWatchlist(this, item, "add".equals(what));
                feed.save(this);
                getContentResolver().notifyChange(HeroProvider.ITEMS, null);   // Home reloads the feed when it is next shown
            }
            handler.post(() -> {
                writing = false;
                if (isDestroyed()) return;
                pill.setAlpha(1f);
                if (error != null) {
                    pill.setText(before);
                    Toast.makeText(this, getString(R.string.trakt_write_failed, error), Toast.LENGTH_LONG).show();
                    return;
                }
                if (watchlist) {
                    onWatchlist = "add".equals(what);
                    btnWatchlist.setText(onWatchlist ? R.string.remove_watchlist : R.string.add_watchlist);
                } else {
                    btnWatched.setText(R.string.watched_done);
                }
                Sync.runNow(this, null);   // Trakt's own view of the rows, Up next included
            });
        }, "trakt-write").start();
    }
}
