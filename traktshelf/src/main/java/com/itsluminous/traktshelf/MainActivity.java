package com.itsluminous.traktshelf;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Home: the hero for the focused poster, rows of posters, sign-in / sync / settings pills. */
public final class MainActivity extends Activity implements PosterAdapter.Listener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout rows, chips;
    private ScrollView rowsScroll;
    private ImageView backdrop;
    private TextView heroTag, heroTitle, heroMeta, heroSynopsis, status, btnSignIn;
    private Feed feed;
    private Auth auth;
    private Item shown;
    private String backdropShown;
    private boolean syncedOnce;
    private final BroadcastReceiver synced = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reload();
        }
    };
    private final Runnable tickStatus = this::updateStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Ui.applyFont(findViewById(R.id.root));
        rows = findViewById(R.id.rows);
        rowsScroll = findViewById(R.id.rowsScroll);
        backdrop = findViewById(R.id.backdrop);
        heroTag = findViewById(R.id.heroTag);
        heroTitle = findViewById(R.id.heroTitle);
        Ui.fitOneLine(heroTitle, 40f, 24f);
        heroMeta = findViewById(R.id.heroMeta);
        heroSynopsis = findViewById(R.id.heroSynopsis);
        chips = findViewById(R.id.heroChips);
        status = findViewById(R.id.status);
        btnSignIn = findViewById(R.id.btnSignIn);
        auth = new Auth(this);
        btnSignIn.setOnClickListener(v -> startActivity(new Intent(this, auth.signedIn() ? SettingsActivity.class : SignInActivity.class)));
        findViewById(R.id.btnSync).setOnClickListener(v -> {
            Sync.runNow(this, null);
            updateStatus();
        });
        findViewById(R.id.btnSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        Sync.schedule(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(synced, new IntentFilter(Sync.ACTION_SYNCED), Context.RECEIVER_NOT_EXPORTED);
        reload();
        // A first open with nothing on disk, a feed older than six hours, or an account whose rows are not there yet
        // (the sync after sign-in did not finish): sync now.
        boolean accountRowsMissing = auth.signedIn() && !feed.rows.containsKey(Feed.REC_SHOWS) && !feed.rows.containsKey(Feed.UP_NEXT);
        if (!syncedOnce && (feed.syncedAt == 0 || accountRowsMissing || System.currentTimeMillis() - feed.syncedAt > 6 * 3600_000L) && !Sync.isRunning()) {
            syncedOnce = true;
            Sync.runNow(this, null);
        }
        updateStatus();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(synced);
        handler.removeCallbacks(tickStatus);
    }

    private void reload() {
        feed = Feed.load(this);
        btnSignIn.setText(auth.signedIn() ? (auth.username() != null ? auth.username() : getString(R.string.account)) : getString(R.string.sign_in));
        View focused = getCurrentFocus();
        String focusedKey = shown == null ? null : shown.key();
        rows.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        View firstCardRow = null;
        for (Feed.Row row : feed.rows.values()) {
            if (row.items.isEmpty()) continue;
            View v = inflater.inflate(R.layout.row, rows, false);
            Ui.applyFont(v);
            ((TextView) v.findViewById(R.id.rowTitle)).setText(row.titleRes());
            RecyclerView list = v.findViewById(R.id.rowList);
            list.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
            list.setItemViewCacheSize(4);
            list.setAdapter(new PosterAdapter(row.items, this));
            list.setTag(row);
            rows.addView(v);
            if (firstCardRow == null) firstCardRow = v;
        }
        if (rows.getChildCount() == 0) {
            heroTag.setText("");
            heroTitle.setText(getString(R.string.app_name));
            heroMeta.setText("");
            heroSynopsis.setText(feed.syncedAt == 0 ? R.string.empty_never_synced : R.string.empty_signed_out);
            chips.removeAllViews();
            return;
        }
        // Focus the first poster unless a pill has focus, and keep the hero on the item it showed.
        if (focused == null || !(focused.getParent() instanceof LinearLayout && ((View) focused.getParent()).getId() == R.id.topButtons)) {
            final View row0 = firstCardRow;
            rows.post(() -> {
                RecyclerView list = row0.findViewById(R.id.rowList);
                View first = list.getChildAt(0);
                if (first != null) first.requestFocus();
            });
        }
        if (focusedKey != null) {
            Item again = feed.find(shown.kind, shown.traktId);
            if (again != null) showHero(again, null);
        } else if (!auth.signedIn()) {
            heroTag.setText("");
            heroTitle.setText(getString(R.string.app_name));
            heroMeta.setText("");
            heroSynopsis.setText(R.string.empty_signed_out);
            chips.removeAllViews();
        }
    }

    private void updateStatus() {
        handler.removeCallbacks(tickStatus);
        if (Sync.isRunning()) {
            String p = Sync.progress();
            status.setText(p == null ? getString(R.string.syncing) : p);
            handler.postDelayed(tickStatus, 700);
        } else if (feed != null && feed.lastError != null) {
            status.setText(feed.lastError);
        } else if (feed != null && feed.syncedAt > 0) {
            status.setText(getString(R.string.last_sync, DateUtils.getRelativeTimeSpanString(feed.syncedAt)));
        } else status.setText("");
    }

    @Override
    public void onFocused(Item item, View card) {
        showHero(item, card);
        // Scroll the focused row to the top of the rows area.
        View rowView = (View) card.getParent().getParent();
        int top = rowView.getTop();
        if (Math.abs(rowsScroll.getScrollY() - top) > 2) rowsScroll.smoothScrollTo(0, top);
    }

    @Override
    public void onClicked(Item item) {
        startActivity(DetailsActivity.intent(this, item));
    }

    private void showHero(Item it, View card) {
        shown = it;
        String tag;
        if (it.nextEpisode != null) tag = getString(R.string.row_up_next) + " · " + it.nextEpisode;
        else {
            Feed.Row row = card == null ? null : (Feed.Row) ((View) card.getParent()).getTag();
            tag = (row != null ? getString(row.titleRes()) : "") + " · " + getString(Item.SHOW.equals(it.kind) ? R.string.show : R.string.movie);
        }
        String coming = Ui.coming(this, it);
        heroTag.setText(coming == null ? tag : coming + " · " + tag);
        heroTitle.setText(it.title);
        heroMeta.setText(it.metaLine());
        heroSynopsis.setText(it.overview);
        chips.removeAllViews();
        for (TextView chip : Ui.ratingChips(this, it)) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = (int) Ui.dp(this, 8);
            chips.addView(chip, lp);
        }
        final String file = it.backdropFile();
        if (file.equals(backdropShown)) return;
        backdropShown = file;
        final int w = (int) Ui.dp(this, 600), h = (int) Ui.dp(this, 338);
        PosterAdapter.DECODER.execute(() -> {
            Bitmap b = Images.decode(this, file, w, h);
            handler.post(() -> {
                if (!file.equals(backdropShown)) return;
                backdrop.animate().cancel();
                if (b == null) {
                    backdrop.animate().alpha(0f).setDuration(200).start();
                    return;
                }
                backdrop.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                    backdrop.setImageBitmap(b);
                    backdrop.animate().alpha(1f).setDuration(320).start();
                }).start();
            });
        });
    }

    static List<Item> none() {
        return Feed.none();
    }
}
