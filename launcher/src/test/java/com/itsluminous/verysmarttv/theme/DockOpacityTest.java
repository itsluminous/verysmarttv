package com.itsluminous.verysmarttv.theme;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import com.itsluminous.verysmarttv.settings.SettingsChoices;

public class DockOpacityTest {
    @Test public void aChoiceMadeOnTheTvWinsOverTheTheme() {
        // Theme says 80, the owner picked 30 in Launcher settings.
        assertEquals(30, SettingsChoices.chosenDockOpacity(30, 80));
        assertEquals(100, SettingsChoices.chosenDockOpacity(100, 80));
    }

    @Test public void noChoiceKeepsTheThemeValue() {
        // -1 is what Config passes when nothing is saved (or a theme push cleared it).
        assertEquals(80, SettingsChoices.chosenDockOpacity(-1, 80));
    }

    @Test public void anOutOfRangeChoiceIsIgnored() {
        // Below 30 the band would vanish and the dock lose its shape; the theme key has the same floor.
        assertEquals(80, SettingsChoices.chosenDockOpacity(0, 80));
        assertEquals(80, SettingsChoices.chosenDockOpacity(29, 80));
        assertEquals(80, SettingsChoices.chosenDockOpacity(101, 80));
    }
}
