package com.itsluminous.verysmarttv;

import android.app.Activity;
import android.os.Bundle;

/**
 * The activity behind the eight dummy tiles of the instrumentation APK's manifest (Tile 1 to Tile 8). The tests
 * only count and focus the tiles; opening one shows nothing and returns to Home at once.
 */
public final class TileActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }
}
