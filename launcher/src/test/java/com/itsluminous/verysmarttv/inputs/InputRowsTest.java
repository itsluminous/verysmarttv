package com.itsluminous.verysmarttv.inputs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The Inputs menu's rows, labels, states, folding and tuner choice, as a table (DESIGN.md §8 rule 2, §14.2). */
public class InputRowsTest {
    private static final int CONNECTED = TvInputManager.INPUT_STATE_CONNECTED;
    private static final int DISCONNECTED = TvInputManager.INPUT_STATE_DISCONNECTED;

    private final Map<String, Integer> states = new HashMap<>();

    private static InputRows.Input hdmi(String id, String label, int ignoredState) {
        return new InputRows.Input(id, TvInputInfo.TYPE_HDMI, null, true, false, true, label, null);
    }

    private static InputRows.Input cec(String id, String port, String label, String custom) {
        return new InputRows.Input(id, TvInputInfo.TYPE_HDMI, port, true, false, true, label, custom);
    }

    private static InputRows.Input av(String id, String label) {
        return new InputRows.Input(id, TvInputInfo.TYPE_COMPOSITE, null, true, false, true, label, null);
    }

    private static InputRows.Input tuner(String id, boolean systemApp) {
        return new InputRows.Input(id, TvInputInfo.TYPE_TUNER, null, false, false, systemApp, "Tuner", null);
    }

    private InputRows.Options options(boolean cecStateOnly, boolean connectedOnly) {
        return new InputRows.Options("", false, cecStateOnly, connectedOnly, id -> false);
    }

    private InputRows.Model build(InputRows.Options o, InputRows.Input... inputs) {
        return InputRows.build(Arrays.asList(inputs), o, id -> states.getOrDefault(id, DISCONNECTED));
    }

    private static List<String> labels(InputRows.Model m) {
        String[] out = new String[m.rows.size()];
        for (int i = 0; i < out.length; i++) out[i] = m.rows.get(i).label;
        return Arrays.asList(out);
    }

    @Test
    public void hdmiPortsFirstInLabelOrderThenAvWithASeparator() {
        InputRows.Model m = build(options(false, false), av("av", "AV"), hdmi("hw2", "HDMI 2", 0), hdmi("hw1", "HDMI 1", 0));
        assertEquals(Arrays.asList("HDMI 1", "HDMI 2", "AV"), labels(m));
        assertFalse(m.rows.get(0).separatorBefore);
        assertFalse(m.rows.get(1).separatorBefore);
        assertTrue("the first non-HDMI row after HDMI rows gets a separator", m.rows.get(2).separatorBefore);
        assertTrue(m.rows.get(0).hdmi);
        assertFalse(m.rows.get(2).hdmi);
    }

    @Test
    public void aLabelWithoutHdmiGetsThePrefixAndAvRowsShowNoState() {
        states.put("hw1", CONNECTED);
        states.put("av", CONNECTED);
        InputRows.Model m = build(options(false, false), hdmi("hw1", "1", 0), av("av", "AV"));
        assertEquals("HDMI 1", m.rows.get(0).label);
        assertEquals(CONNECTED, m.rows.get(0).state);
        assertEquals("analog jacks have no hotplug detection", -1, m.rows.get(1).state);
    }

    @Test
    public void everyPortConnectedDropsTheHdmiState() {
        states.put("hw1", CONNECTED);
        states.put("hw2", CONNECTED);
        InputRows.Model m = build(options(false, false), hdmi("hw1", "HDMI 1", 0), hdmi("hw2", "HDMI 2", 0));
        assertEquals(-1, m.rows.get(0).state);
        assertEquals(-1, m.rows.get(1).state);
        // one port alone keeps its state: nothing to compare it with
        InputRows.Model one = build(options(false, false), hdmi("hw1", "HDMI 1", 0));
        assertEquals(CONNECTED, one.rows.get(0).state);
    }

    @Test
    public void cecDeviceTakesItsPortsRowWithItsNameAndThePort() {
        states.put("hw1", DISCONNECTED);
        states.put("hw1/dev", CONNECTED);
        InputRows.Model m = build(options(false, false), hdmi("hw1", "HDMI 1", 0), cec("hw1/dev", "hw1", "Apple TV", null), hdmi("hw2", "HDMI 2", 0));
        assertEquals(Arrays.asList("Apple TV \u00B7 HDMI 1", "HDMI 2"), labels(m));
        assertEquals("the device row opens the device's input", "hw1/dev", m.rows.get(0).inputId);
        assertEquals("the device's own state", CONNECTED, m.rows.get(0).state);
    }

    @Test
    public void customNamesGoFirstAndNameTheDeviceWhenItHasNone() {
        InputRows.Model named = build(options(false, false),
                new InputRows.Input("hw1", TvInputInfo.TYPE_HDMI, null, true, false, true, "HDMI 1", "MacBook Pro"));
        assertEquals("MacBook Pro \u00B7 HDMI 1", named.rows.get(0).label);
        InputRows.Model device = build(options(false, false),
                new InputRows.Input("hw1", TvInputInfo.TYPE_HDMI, null, true, false, true, "HDMI 1", "Living room"),
                cec("hw1/dev", "hw1", null, null));
        assertEquals("the port's custom name names a device without one", "Living room \u00B7 HDMI 1", device.rows.get(0).label);
        InputRows.Model same = build(options(false, false),
                new InputRows.Input("hw1", TvInputInfo.TYPE_HDMI, null, true, false, true, "HDMI 1", "HDMI 1"));
        assertEquals("a custom name equal to the label is not repeated", "HDMI 1", same.rows.get(0).label);
    }

    @Test
    public void cecStateOnlyHidesPortStatesAndKeepsDeviceStates() {
        states.put("hw1", CONNECTED);
        states.put("hw2", DISCONNECTED);
        states.put("hw2/dev", CONNECTED);
        InputRows.Model m = build(options(true, false), hdmi("hw1", "HDMI 1", 0), hdmi("hw2", "HDMI 2", 0), cec("hw2/dev", "hw2", "Soundbar", null));
        assertEquals(-1, m.rows.get(0).state);
        assertEquals(CONNECTED, m.rows.get(1).state);
    }

    @Test
    public void hiddenLeftOutAndNonPassthroughInputsAreNotListed() {
        InputRows.Options o = new InputRows.Options("", false, false, false, id -> id.equals("hw8"));
        InputRows.Model m = build(o,
                hdmi("hw1", "HDMI 1", 0),
                new InputRows.Input("hw3", TvInputInfo.TYPE_HDMI, null, true, true, true, "HDMI 3", null),
                hdmi("hw8", "HDMI 8", 0),
                tuner("tv/tuner/HW1", true));
        assertEquals(Arrays.asList("HDMI 1"), labels(m));
    }

    @Test
    public void connectedOnlyFoldsDisconnectedPortsAvAndTheSeparator() {
        states.put("hw1", CONNECTED);
        states.put("hw2", DISCONNECTED);
        InputRows.Model m = build(options(false, true), hdmi("hw1", "HDMI 1", 0), hdmi("hw2", "HDMI 2", 0), av("av", "AV"));
        assertFalse(m.rows.get(0).folded);
        assertTrue(m.rows.get(1).folded);
        assertTrue("the AV row folds", m.rows.get(2).folded);
        assertTrue("and carries the separator, which the menu folds with it", m.rows.get(2).separatorBefore);
    }

    @Test
    public void tunerPrefersHardwareThenDigitalThenIdOrder() {
        InputRows.Model m = build(options(false, false),
                tuner("com.airplay/.Tuner", true),
                tuner("tv/AnalogInputService/HW2", true),
                tuner("tv/TunerInputService/HW1", true));
        assertEquals("tv/TunerInputService/HW1", m.tunerId);
        InputRows.Model analogOnly = build(options(false, false), tuner("tv/AnalogInputService/HW2", true), tuner("soft/b", true));
        assertEquals("a hardware tuner beats a software one even when analog", "tv/AnalogInputService/HW2", analogOnly.tunerId);
        InputRows.Model soft = build(options(false, false), tuner("soft/b", true), tuner("soft/a", true));
        assertEquals("same kind: the smaller id", "soft/a", soft.tunerId);
    }

    @Test
    public void tunerHonoursLiveTvInputIdAndSkipsNonSystemApps() {
        InputRows.Options explicit = new InputRows.Options("tv/TunerInputService/HW2", false, false, false, id -> false);
        InputRows.Model m = build(explicit, tuner("tv/TunerInputService/HW1", true), tuner("tv/TunerInputService/HW2", true));
        assertEquals("tv/TunerInputService/HW2", m.tunerId);
        InputRows.Model absent = build(explicit, tuner("tv/TunerInputService/HW1", true));
        assertNull("the configured input is absent: no tuning, the app opens normally", absent.tunerId);
        InputRows.Options root = new InputRows.Options("content://android.media.tv/channel", true, false, false, id -> false);
        assertEquals("the root channel URI takes any system tuner", "tv/TunerInputService/HW1",
                build(root, tuner("tv/TunerInputService/HW1", true)).tunerId);
        assertNull("a third-party tuner never qualifies", build(options(false, false), tuner("x/HW1", false)).tunerId);
    }

    @Test
    public void noInputsNoRowsNoTuner() {
        InputRows.Model m = build(options(false, false));
        assertTrue(m.rows.isEmpty());
        assertNull(m.tunerId);
    }
}
