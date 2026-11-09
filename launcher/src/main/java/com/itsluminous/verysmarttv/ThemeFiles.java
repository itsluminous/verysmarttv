package com.itsluminous.verysmarttv;

import java.util.List;

/** The files ThemeProvider may write or delete under Config.dir, by the URI's path segments. */
final class ThemeFiles {
    private ThemeFiles() {}

    /** The path relative to the theme directory: "config.json", "wallpaper.jpg" or "theme_revision", or null if refused. */
    static String path(List<String> segments) {
        if (segments == null || segments.size() != 1) return null;
        String name = segments.get(0);
        return Config.FILE_NAME.equals(name) || Config.WALLPAPER_NAME.equals(name) || "theme_revision".equals(name)
                ? name : null;
    }
}
