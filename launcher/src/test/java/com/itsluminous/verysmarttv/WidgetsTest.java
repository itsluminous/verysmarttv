package com.itsluminous.verysmarttv;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;

/** widgets: the Widgets tick list over the theme's key, both kept to the three chips in the bar's order. */
public class WidgetsTest {

    @Test
    public void theThemesListIsKeptToTheThreeNamesInBarOrder() {
        assertEquals(Arrays.asList("memory", "cpu", "storage"), Config.widgetList(Arrays.asList("storage", "cpu", "memory")));
        assertEquals(Arrays.asList("cpu"), Config.widgetList(Arrays.asList("cpu", "cpu", "gpu")));
        assertEquals(Collections.emptyList(), Config.widgetList(Collections.emptyList()));
    }

    @Test
    public void aSavedSetWinsOverTheThemeAndAnEmptySetIsASavedNone() {
        List<String> theme = Arrays.asList("memory", "cpu", "storage");
        assertEquals(theme, Config.chosenWidgets(null, theme));
        assertEquals(Arrays.asList("cpu"), Config.chosenWidgets(new HashSet<>(Collections.singletonList("cpu")), theme));
        assertEquals(Collections.emptyList(), Config.chosenWidgets(new HashSet<>(), theme));
        assertEquals(Arrays.asList("memory", "storage"), Config.chosenWidgets(new HashSet<>(Arrays.asList("storage", "memory", "x")), Collections.emptyList()));
    }
}
