package com.itsluminous.verysmarttv.home;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;

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
}
