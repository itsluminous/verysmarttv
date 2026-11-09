package com.itsluminous.traktshelf;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The carousel and watchlist settings, in the "settings" SharedPreferences (the default player is there too, Players).
 * The provider applies them on every query; SettingsActivity notifies its observers when one changes.
 */
final class Prefs {
    /** Which rows feed the launcher's carousel (Feed row keys); all but Recently watched by default. */
    static final String KEY_HERO_ROWS = "hero_rows";
    /** How many items the carousel gets: 6, 9, 12, 15 or 20. */
    static final String KEY_HERO_ITEMS = "hero_items";
    /** The watchlist's order, on Home and in the carousel: added (newest first) or release (soonest, then newest). */
    static final String KEY_WATCHLIST_ORDER = "watchlist_order";
    static final String ORDER_ADDED = "added", ORDER_RELEASE = "release";
    static final int[] HERO_ITEM_CHOICES = {6, 9, 12, 15, 20};
    static final int HERO_ITEMS_DEFAULT = 12;

    private Prefs() {
    }

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(Players.PREFS, Context.MODE_PRIVATE);
    }

    static Set<String> defaultHeroRows() {
        Set<String> s = new LinkedHashSet<>(Arrays.asList(Feed.ROW_ORDER));
        s.remove(Feed.HISTORY);
        return s;
    }

    /**
     * The rows the carousel draws from, in Feed.ROW_ORDER. A set saved before the watchlist split names one
     * "watchlist" row: that tick stands for both Watchlist · Shows and Watchlist · Movies until the next change
     * saves the two keys.
     */
    static Set<String> heroRows(Context c) {
        Set<String> saved = get(c).getStringSet(KEY_HERO_ROWS, null);
        if (saved == null) return defaultHeroRows();
        Set<String> out = new LinkedHashSet<>();
        boolean legacy = saved.contains(Feed.LEGACY_WATCHLIST);
        for (String key : Feed.ROW_ORDER) if (saved.contains(key) || (legacy && Feed.isWatchlistRow(key))) out.add(key);
        return out;
    }

    static void setHeroRows(Context c, Set<String> rows) {
        get(c).edit().putStringSet(KEY_HERO_ROWS, new LinkedHashSet<>(rows)).apply();
    }

    static int heroItems(Context c) {
        int n = get(c).getInt(KEY_HERO_ITEMS, HERO_ITEMS_DEFAULT);
        for (int choice : HERO_ITEM_CHOICES) if (choice == n) return n;
        return HERO_ITEMS_DEFAULT;
    }

    static void setHeroItems(Context c, int n) {
        get(c).edit().putInt(KEY_HERO_ITEMS, n).apply();
    }

    static String watchlistOrder(Context c) {
        return ORDER_RELEASE.equals(get(c).getString(KEY_WATCHLIST_ORDER, ORDER_ADDED)) ? ORDER_RELEASE : ORDER_ADDED;
    }

    static void setWatchlistOrder(Context c, String order) {
        get(c).edit().putString(KEY_WATCHLIST_ORDER, order).apply();
    }
}
