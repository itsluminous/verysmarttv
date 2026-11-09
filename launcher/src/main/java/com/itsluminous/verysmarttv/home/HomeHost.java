package com.itsluminous.verysmarttv.home;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.itsluminous.verysmarttv.cards.CardPrompts;
import com.itsluminous.verysmarttv.clock.IdleClock;
import com.itsluminous.verysmarttv.grid.AppAdapter;
import com.itsluminous.verysmarttv.grid.DockEditSession;
import com.itsluminous.verysmarttv.grid.GridController;
import com.itsluminous.verysmarttv.grid.ReorderSession;
import com.itsluminous.verysmarttv.inputs.InputsMenu;
import com.itsluminous.verysmarttv.keys.KeyDispatcher;
import com.itsluminous.verysmarttv.menus.GlassPopups;
import com.itsluminous.verysmarttv.shelf.AmbientGlow;
import com.itsluminous.verysmarttv.shelf.CarouselController;
import com.itsluminous.verysmarttv.shelf.ShelfController;
import com.itsluminous.verysmarttv.topbar.TopBarController;

/**
 * What the controllers of DESIGN.md §8 need from the Home activity: its views, the shared {@link HomeState}, the
 * main-thread handler, a way to open an app, and each other. {@link HomeActivity} implements it; a controller
 * takes a {@code HomeHost} in its constructor and never reaches into another controller's views.
 */
public interface HomeHost {
    /** The activity as a Context: inflating, toasts, system services, {@code startActivity}, prefs, focus queries. */
    @NonNull
    Activity activity();

    @NonNull
    HomeState state();

    /** The main-thread handler every timer and post goes through. */
    @NonNull
    Handler handler();

    float density();

    /** Whether the framework lays this screen out right-to-left (supportsRtl). */
    boolean isRtl();

    /** Starts an app in a new task; toasts "Not available" when nothing answers or the start is refused. */
    void open(@NonNull Intent i);

    <T extends View> T findViewById(int id);

    /** The activity's own {@code super.dispatchKeyEvent}: the key goes on to the focused view. */
    boolean dispatchKeyEventToViews(@NonNull KeyEvent event);

    /** The adapter's listener: the activity, which hands each call to the grid controller. */
    @NonNull
    AppAdapter.TileListener tileListener();

    // ---- the controllers, for each other ----

    @NonNull
    StartupRouter startup();

    @NonNull
    CardPrompts cards();

    @NonNull
    GlassPopups popups();

    @NonNull
    InputsMenu inputs();

    @NonNull
    TopBarController topBar();

    @NonNull
    GridController grid();

    @NonNull
    ShelfController shelf();

    @NonNull
    CarouselController carousel();

    @NonNull
    AmbientGlow glow();

    @NonNull
    ReorderSession reorder();

    @NonNull
    DockEditSession dockEdit();

    @NonNull
    KeyDispatcher keys();

    @NonNull
    IdleClock idleClock();
}
