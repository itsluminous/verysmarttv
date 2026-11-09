package com.itsluminous.verysmarttv.topbar;

import androidx.annotation.NonNull;

import com.itsluminous.verysmarttv.theme.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which pills the top bar shows for a theme, in bar order (DESIGN.md §8 rule 2, §14.2 PillOrderTest). The theme's
 * keys decide the static part here; two pills also hide at run time, which the controller keeps doing: Inputs when
 * the TV has no inputs (updateInputsButton) and VPN while no VPN is up and none is configured (updateVpnPill).
 * A top_bar_apps button shows only while its app is installed (addTopBarApps).
 *
 * <p>Phase 3's score pill sits under the bar, not in it (ADR-017), so it is no row here.
 */
public final class PillOrder {
    private PillOrder() {
    }

    /** The pills, in the bar's child order (activity_main.xml; top_bar_apps go between Inputs and the network pill). */
    public enum Pill { INPUTS, APP, NETWORK, VPN, REORDER, LIBRARY, SETTINGS }

    /** One pill of the bar: which, and whether the theme shows it; an APP row carries its package. */
    public static final class Entry {
        public final Pill pill;
        public final boolean shown;
        /** The top_bar_apps package for {@link Pill#APP}; empty otherwise. */
        public final String pkg;

        Entry(Pill pill, boolean shown, @NonNull String pkg) {
            this.pill = pill;
            this.shown = shown;
            this.pkg = pkg;
        }

        @Override
        @NonNull
        public String toString() {
            return (pill == Pill.APP ? "APP:" + pkg : pill.name()) + (shown ? "" : "(hidden)");
        }
    }

    /** The theme keys the bar reads. */
    public static final class Keys {
        public final boolean showInputsPill, showNetworkPill, vpnPill, reorderButton, libraryButton, appLibrary, topMenu;
        public final List<String> topBarApps;

        public Keys(boolean showInputsPill, boolean showNetworkPill, boolean vpnPill, boolean reorderButton, boolean libraryButton,
                    boolean appLibrary, boolean topMenu, @NonNull List<String> topBarApps) {
            this.showInputsPill = showInputsPill;
            this.showNetworkPill = showNetworkPill;
            this.vpnPill = vpnPill;
            this.reorderButton = reorderButton;
            this.libraryButton = libraryButton;
            this.appLibrary = appLibrary;
            this.topMenu = topMenu;
            this.topBarApps = topBarApps;
        }

        @NonNull
        public static Keys of(@NonNull Config cfg) {
            return new Keys(cfg.showInputsPill, cfg.showNetworkPill, cfg.vpnPill, cfg.reorderButton, cfg.libraryButton,
                    cfg.appLibrary, cfg.topMenu, cfg.topBarApps);
        }
    }

    /**
     * The bar for these keys. top_menu folds Inputs and the network pill into the gear, so both hide and vpn_pill is
     * off there; library_button needs app_library; Settings always shows.
     */
    @NonNull
    public static List<Entry> of(@NonNull Keys k) {
        List<Entry> out = new ArrayList<>();
        out.add(new Entry(Pill.INPUTS, k.showInputsPill && !k.topMenu, ""));
        for (String pkg : k.topBarApps) out.add(new Entry(Pill.APP, true, pkg));
        out.add(new Entry(Pill.NETWORK, k.showNetworkPill && !k.topMenu, ""));
        out.add(new Entry(Pill.VPN, k.vpnPill && !k.topMenu, ""));
        out.add(new Entry(Pill.REORDER, k.reorderButton, ""));
        out.add(new Entry(Pill.LIBRARY, k.libraryButton && k.appLibrary, ""));
        out.add(new Entry(Pill.SETTINGS, true, ""));
        return Collections.unmodifiableList(out);
    }

    /** Whether the theme shows this pill (the run-time hides above still apply). */
    public static boolean shown(@NonNull List<Entry> bar, @NonNull Pill pill) {
        for (Entry e : bar) if (e.pill == pill) return e.shown;
        return false;
    }
}
