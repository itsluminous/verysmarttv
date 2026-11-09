package com.itsluminous.verysmarttv.theme;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ThemeFilesTest {
    @Test public void themeFilesAreAllowed() {
        assertEquals("config.json", ThemeFiles.path(Collections.singletonList("config.json")));
        assertEquals("wallpaper.jpg", ThemeFiles.path(Collections.singletonList("wallpaper.jpg")));
        assertEquals("theme_revision", ThemeFiles.path(Collections.singletonList("theme_revision")));
    }

    /** The fork's wallpapers/ collection went with the rotation (ADR-008): a push of one is refused like any other path. */
    @Test public void aCollectionIsRefused() {
        assertNull(ThemeFiles.path(Collections.singletonList("wallpapers")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "dusk.jpg")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "captions.txt")));
    }

    @Test public void anythingElseIsRefused() {
        assertNull(ThemeFiles.path(null));
        assertNull(ThemeFiles.path(Collections.emptyList()));
        assertNull(ThemeFiles.path(Collections.singletonList("cards.json")));
        assertNull(ThemeFiles.path(Collections.singletonList("captions.txt")));
        assertNull(ThemeFiles.path(Collections.singletonList("..")));
        assertNull(ThemeFiles.path(Arrays.asList("..", "config.json")));
        assertNull(ThemeFiles.path(Arrays.asList("other", "wallpaper.jpg")));
        assertNull(ThemeFiles.path(Arrays.asList("config.json", "x")));
    }
}
