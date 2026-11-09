package com.itsluminous.verysmarttv.theme;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** glass_style from config.xml or a theme: rim or halo by name, glass for anything else. */
public class GlassStyleTest {

    @Test
    public void rimByNameAnyCase() {
        assertEquals(ThemeKeys.GLASS_RIM, ThemeKeys.glassStyle("rim"));
        assertEquals(ThemeKeys.GLASS_RIM, ThemeKeys.glassStyle(" Rim "));
    }

    @Test
    public void haloByNameAnyCase() {
        assertEquals(ThemeKeys.GLASS_HALO, ThemeKeys.glassStyle("halo"));
        assertEquals(ThemeKeys.GLASS_HALO, ThemeKeys.glassStyle("HALO "));
    }

    @Test
    public void anythingElseIsGlass() {
        assertEquals(ThemeKeys.GLASS_DEFAULT, ThemeKeys.glassStyle("glass"));
        assertEquals(ThemeKeys.GLASS_DEFAULT, ThemeKeys.glassStyle(""));
        assertEquals(ThemeKeys.GLASS_DEFAULT, ThemeKeys.glassStyle(null));
        assertEquals(ThemeKeys.GLASS_DEFAULT, ThemeKeys.glassStyle("frosted"));
    }
}
