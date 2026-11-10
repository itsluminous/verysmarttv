package com.itsluminous.verysmarttv.topbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * The carousel's backdrop faded under the score stack (DESIGN.md §9.2, ADR-046): a dark veil over the region
 * {@link ScoreStackLayout#shadeBounds} names, solid behind the lines and running out to transparent over
 * {@link #FADE_DP} along its bottom edge and the edge towards the screen's middle, so the backdrop melts away under the stack the way its own baked-in
 * fades melt it into the wallpaper, with no edge. Drawn in the root between the backdrop and the stack, and only while
 * a backdrop is up and the stack shows; {@code ScorePillController} sizes it with the stack. Two linear gradients
 * multiplied, no bitmap, so it costs a 1 GB TV nothing to keep.
 */
public final class ScoreStackShade extends View {
    /** The fade's run, dp, along the left and bottom edges; the veil is solid inside it. */
    public static final int FADE_DP = 24;
    /** The veil's darkness over the backdrop: the round pills' over-art base ({@code pill_bg_over_art}) is 40%. */
    static final int VEIL = 0xB3000000;
    private final Paint paint = new Paint();
    private boolean mirrored;

    public ScoreStackShade(@NonNull Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setVisibility(GONE);
    }

    /** RTL: the stack hangs under the bar's left end, so the veil runs out along its right edge instead of its left. */
    public void setMirrored(boolean rtl) {
        if (mirrored == rtl) return;
        mirrored = rtl;
        shade(getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        shade(w, h);
    }

    /** The veil for a size: built here, not in onDraw, once per size. */
    private void shade(int w, int h) {
        if (w <= 0 || h <= 0) {
            paint.setShader(null);
            return;
        }
        float fade = Math.min(FADE_DP * getResources().getDisplayMetrics().density, Math.min(w, h));
        // Alpha only: both gradients are black, MULTIPLY gives the product of their alphas, so the veil is VEIL where
        // both are full and runs out along either edge.
        Shader across = mirrored
                ? new LinearGradient(w - fade, 0, w, 0, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                : new LinearGradient(0, 0, fade, 0, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP);
        Shader down = new LinearGradient(0, h - fade, 0, h, VEIL, Color.TRANSPARENT, Shader.TileMode.CLAMP);
        paint.setShader(new ComposeShader(across, down, PorterDuff.Mode.MULTIPLY));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (paint.getShader() != null) canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }
}
