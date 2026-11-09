package com.itsluminous.traktshelf;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.app.AlertDialog;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Account, Sync now, Default player, Carousel rows, Carousel items, Watchlist order, Sign out. */
public final class SettingsActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private ScrollView scroll;
    private Auth auth;
    private TextView syncValue;
    private final Runnable tick = this::updateSync;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Ui.applyFont(findViewById(android.R.id.content));
        list = findViewById(R.id.list);
        scroll = findViewById(R.id.scroll);
        auth = new Auth(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private void build() {
        list.removeAllViews();
        boolean signedIn = auth.signedIn();
        row(getString(R.string.account), signedIn ? (auth.username() != null ? auth.username() : "Trakt") : getString(R.string.not_signed_in),
                v -> {
                    if (!signedIn) startActivity(new Intent(this, SignInActivity.class));
                });
        syncValue = row(getString(R.string.sync_now), "", v -> {
            Sync.runNow(this, null);
            updateSync();
        });
        updateSync();
        List<Players.Player> players = Players.installedPlayers(this);
        row(getString(R.string.default_player), playerLabel(players), v -> {
            String cur = Players.defaultPlayer(this);
            int at = -1;
            for (int i = 0; i < players.size(); i++) if (players.get(i).id.equals(cur)) at = i;
            // Cycle: ask, then each installed player.
            String next = at + 1 < players.size() ? players.get(at + 1).id : "ask";
            if (cur.equals("ask") && !players.isEmpty()) next = players.get(0).id;
            Players.setDefaultPlayer(this, next);
            ((TextView) v.findViewById(R.id.rowValue)).setText(playerLabel(players));
        });
        row(getString(R.string.hero_rows), heroRowsLabel(), v -> pickHeroRows((TextView) v.findViewById(R.id.rowValue)));
        row(getString(R.string.hero_items), String.valueOf(Prefs.heroItems(this)), v -> {
            int cur = Prefs.heroItems(this), next = Prefs.HERO_ITEM_CHOICES[0];
            for (int i = 0; i < Prefs.HERO_ITEM_CHOICES.length; i++) {
                if (Prefs.HERO_ITEM_CHOICES[i] == cur && i + 1 < Prefs.HERO_ITEM_CHOICES.length) next = Prefs.HERO_ITEM_CHOICES[i + 1];
            }
            Prefs.setHeroItems(this, next);
            ((TextView) v.findViewById(R.id.rowValue)).setText(String.valueOf(next));
            settingsChanged();
        });
        row(getString(R.string.watchlist_order), watchlistOrderLabel(), v -> {
            boolean release = Prefs.ORDER_RELEASE.equals(Prefs.watchlistOrder(this));
            Prefs.setWatchlistOrder(this, release ? Prefs.ORDER_ADDED : Prefs.ORDER_RELEASE);
            ((TextView) v.findViewById(R.id.rowValue)).setText(watchlistOrderLabel());
            settingsChanged();
            Sync.runNow(this, null);   // the rows are Trakt's first 20 in this order: fetch them again
            updateSync();
        });
        if (signedIn) row(getString(R.string.sign_out), "", v -> {
            auth.signOut();
            Feed feed = new Feed();
            feed.save(this);
            getContentResolver().notifyChange(HeroProvider.ITEMS, null);
            Sync.runNow(this, null);
            build();
        });
        View first = list.getChildAt(0);
        if (first != null) first.requestFocus();
        // The rows outrun a 540dp screen; the ScrollView brings a focused row into view, and the last row brings the
        // footnote under it along (nothing there takes focus).
        View last = list.getChildAt(list.getChildCount() - 1);
        if (last != null) last.setOnFocusChangeListener((v, has) -> {
            if (has) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    /** The carousel reads the provider again: Home's shelf follows a changed row set, count or order at once. */
    private void settingsChanged() {
        getContentResolver().notifyChange(HeroProvider.ITEMS, null);
    }

    private String heroRowsLabel() {
        Set<String> on = Prefs.heroRows(this);
        if (on.isEmpty()) return getString(R.string.none);
        if (on.size() == Feed.ROW_ORDER.length) return getString(R.string.all_rows);
        List<String> names = new ArrayList<>();
        for (String key : Feed.ROW_ORDER) if (on.contains(key)) names.add(getString(new Feed.Row(key).titleRes()));
        return String.join(", ", names);
    }

    /** A tick list of the six rows; OK on an entry flips it, Back keeps what is ticked. */
    private void pickHeroRows(TextView value) {
        Set<String> on = new LinkedHashSet<>(Prefs.heroRows(this));
        String[] names = new String[Feed.ROW_ORDER.length];
        boolean[] checked = new boolean[Feed.ROW_ORDER.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = getString(new Feed.Row(Feed.ROW_ORDER[i]).titleRes());
            checked[i] = on.contains(Feed.ROW_ORDER[i]);
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_NoActionBar)
                .setTitle(R.string.hero_rows)
                .setMultiChoiceItems(names, checked, (d, which, isChecked) -> {
                    if (isChecked) on.add(Feed.ROW_ORDER[which]);
                    else on.remove(Feed.ROW_ORDER[which]);
                    Prefs.setHeroRows(this, on);
                    value.setText(heroRowsLabel());
                    value.setVisibility(View.VISIBLE);
                    settingsChanged();
                })
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private String watchlistOrderLabel() {
        return getString(Prefs.ORDER_RELEASE.equals(Prefs.watchlistOrder(this)) ? R.string.order_release : R.string.order_added);
    }

    private String playerLabel(List<Players.Player> players) {
        String cur = Players.defaultPlayer(this);
        for (Players.Player p : players) if (p.id.equals(cur)) return p.label;
        return getString(R.string.player_ask);
    }

    private void updateSync() {
        handler.removeCallbacks(tick);
        syncValue.setVisibility(View.VISIBLE);
        if (Sync.isRunning()) {
            String p = Sync.progress();
            syncValue.setText(p == null ? getString(R.string.syncing) : p);
            handler.postDelayed(tick, 700);
        } else {
            Feed feed = Feed.load(this);
            String when = feed.syncedAt == 0 ? getString(R.string.never) : DateUtils.getRelativeTimeSpanString(feed.syncedAt).toString();
            String last = getString(R.string.last_sync, when);
            syncValue.setText(feed.lastError != null ? getString(R.string.last_sync_error, last, feed.lastError) : last);
        }
    }

    private TextView row(String name, String value, View.OnClickListener click) {
        View v = LayoutInflater.from(this).inflate(R.layout.settings_row, list, false);
        Ui.applyFont(v);
        ((TextView) v.findViewById(R.id.rowName)).setText(name);
        TextView val = v.findViewById(R.id.rowValue);
        val.setText(value);
        val.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
        v.setOnClickListener(click);
        list.addView(v);
        return val;
    }
}
