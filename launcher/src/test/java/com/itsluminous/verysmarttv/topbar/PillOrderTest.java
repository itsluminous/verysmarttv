package com.itsluminous.verysmarttv.topbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Which pills show for each combination of the top-bar keys (DESIGN.md §14.2). */
public class PillOrderTest {
    private static PillOrder.Keys keys(boolean inputs, boolean network, boolean vpn, boolean reorder, boolean library,
                                       boolean appLibrary, boolean topMenu, String... apps) {
        return new PillOrder.Keys(inputs, network, vpn, reorder, library, appLibrary, topMenu, Arrays.asList(apps));
    }

    private static String shown(List<PillOrder.Entry> bar) {
        StringBuilder b = new StringBuilder();
        for (PillOrder.Entry e : bar) {
            if (!e.shown) continue;
            if (b.length() > 0) b.append(' ');
            b.append(e.pill == PillOrder.Pill.APP ? e.pkg : e.pill.name());
        }
        return b.toString();
    }

    @Test
    public void everythingOnInBarOrder() {
        List<PillOrder.Entry> bar = PillOrder.of(keys(true, true, true, true, true, true, false, "com.a", "com.b"));
        assertEquals("INPUTS com.a com.b NETWORK VPN REORDER LIBRARY SETTINGS", shown(bar));
        assertEquals("the order is the layout's, apps between Inputs and the network pill", 8, bar.size());
    }

    @Test
    public void theDefaultTheme() {
        // config.xml: Inputs, the network pill and Settings; no VPN pill, no reorder or library button, no apps
        List<PillOrder.Entry> bar = PillOrder.of(keys(true, true, false, false, false, false, false));
        assertEquals("INPUTS NETWORK SETTINGS", shown(bar));
    }

    @Test
    public void eachSwitchHidesItsPill() {
        assertEquals("NETWORK SETTINGS", shown(PillOrder.of(keys(false, true, false, false, false, false, false))));
        assertEquals("INPUTS SETTINGS", shown(PillOrder.of(keys(true, false, false, false, false, false, false))));
        assertEquals("INPUTS NETWORK REORDER SETTINGS", shown(PillOrder.of(keys(true, true, false, true, false, false, false))));
        assertEquals("INPUTS NETWORK VPN SETTINGS", shown(PillOrder.of(keys(true, true, true, false, false, false, false))));
    }

    @Test
    public void libraryButtonNeedsTheAppLibrary() {
        assertEquals("INPUTS NETWORK SETTINGS", shown(PillOrder.of(keys(true, true, false, false, true, false, false))));
        assertEquals("INPUTS NETWORK LIBRARY SETTINGS", shown(PillOrder.of(keys(true, true, false, false, true, true, false))));
        assertEquals("app_library alone adds no pill", "INPUTS NETWORK SETTINGS", shown(PillOrder.of(keys(true, true, false, false, false, true, false))));
    }

    @Test
    public void topMenuFoldsInputsNetworkAndVpnIntoTheGear() {
        List<PillOrder.Entry> bar = PillOrder.of(keys(true, true, true, true, true, true, true, "com.a"));
        assertEquals("com.a REORDER LIBRARY SETTINGS", shown(bar));
        assertFalse(PillOrder.shown(bar, PillOrder.Pill.INPUTS));
        assertFalse(PillOrder.shown(bar, PillOrder.Pill.NETWORK));
        assertFalse(PillOrder.shown(bar, PillOrder.Pill.VPN));
        assertTrue("Settings always shows", PillOrder.shown(bar, PillOrder.Pill.SETTINGS));
    }

    @Test
    public void settingsAloneAtTheLeast() {
        List<PillOrder.Entry> bar = PillOrder.of(new PillOrder.Keys(false, false, false, false, false, false, true, Collections.emptyList()));
        assertEquals("SETTINGS", shown(bar));
        assertEquals("the hidden pills are still rows, so the bar's shape is known", 6, bar.size());
    }

    @Test
    public void appsKeepTheirListOrder() {
        List<PillOrder.Entry> bar = PillOrder.of(keys(true, true, false, false, false, false, false, "z.last", "a.first"));
        assertEquals("INPUTS z.last a.first NETWORK SETTINGS", shown(bar));
        assertEquals(PillOrder.Pill.APP, bar.get(1).pill);
        assertEquals("z.last", bar.get(1).pkg);
    }
}
