package com.itsluminous.verysmarttv.testkit.tiles;

import android.app.Activity;
import android.os.Bundle;

import androidx.annotation.Nullable;

/** Tile 7's activity: the tests only count and focus the tile; opening it shows nothing and returns to Home. */
public final class TileActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }
}
