package com.itsluminous.verysmarttv.theme;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** glow_style from config.xml or a theme: horizon by name, radial for anything else. */
public class GlowStyleTest {

    @Test
    public void horizonByNameAnyCase() {
        assertEquals(ThemeKeys.GLOW_HORIZON, ThemeKeys.glowStyle("horizon"));
        assertEquals(ThemeKeys.GLOW_HORIZON, ThemeKeys.glowStyle(" Horizon "));
    }

    @Test
    public void anythingElseIsRadial() {
        assertEquals(ThemeKeys.GLOW_RADIAL, ThemeKeys.glowStyle("radial"));
        assertEquals(ThemeKeys.GLOW_RADIAL, ThemeKeys.glowStyle(""));
        assertEquals(ThemeKeys.GLOW_RADIAL, ThemeKeys.glowStyle(null));
        assertEquals(ThemeKeys.GLOW_RADIAL, ThemeKeys.glowStyle("aurora"));
    }
}
