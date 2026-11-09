package com.itsluminous.verysmarttv.shelf;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;

import com.itsluminous.verysmarttv.home.HomeHost;
import com.itsluminous.verysmarttv.theme.Config;

/**
 * The ambient glow behind the grid: one small bitmap stretched over the screen, tinted with the focused app's colour
 * (DESIGN.md §8, moved from the activity verbatim). The shelf asks it for a colour, a colour transition or a fade;
 * nothing else touches the view.
 */
public final class AmbientGlow {
    private final HomeHost host;
    private final View ambient;
    /** config.xml ambient_glow, overridable by a theme. Off: the glow view is gone and no fade or colour animator
     *  is started for it, so a focus move costs nothing extra on a launcher that runs all day. */
    private final boolean ambientGlow;
    /** config.xml glow_style: the shape setAmbient paints the glow mask in. */
    private final String glowStyle;
    /** config.xml glow_snap: the glow takes a new colour in one frame instead of the dip-and-fade. */
    private final boolean glowSnap;
    private int ambientColor = -1;
    private ValueAnimator ambientAnimator;

    public AmbientGlow(@NonNull HomeHost host, @NonNull View ambient, @NonNull Config cfg) {
        this.host = host;
        this.ambient = ambient;
        // The glow is one bitmap, so its alpha can be applied while drawing it. Otherwise every frame of
        // a fade renders it into a full-screen offscreen buffer first.
        ambient.forceHasOverlappingRendering(false);
        ambientGlow = cfg.ambientGlow;
        glowSnap = cfg.glowSnap;
        glowStyle = cfg.glowStyle;
        if (!ambientGlow) ambient.setVisibility(View.GONE);
    }

    /** ambient_glow: whether the glow exists at all. */
    public boolean on() {
        return ambientGlow;
    }

    /** glow_snap: a new colour in one frame instead of the dip-and-fade. */
    public boolean snaps() {
        return glowSnap;
    }

    /** The colour the mask is tinted with, -1 before the first. */
    public int color() {
        return ambientColor;
    }

    /** The mask has been painted (setAmbient ran once). */
    public boolean hasMask() {
        return ambient.getBackground() != null;
    }

    public float getAlpha() {
        return ambient.getAlpha();
    }

    public void setAlpha(float alpha) {
        ambient.setAlpha(alpha);
    }

    /** Cancels a running fade (not the colour animator: {@link #stopAmbientAnimation}). */
    public void cancelFade() {
        ambient.animate().cancel();
    }

    public void fadeTo(float alpha, int ms) {
        ambient.animate().alpha(alpha).setDuration(ms).start();
    }

    /** The dip-and-swap the shelf uses: down to 0.35 over dipMs, the new colour, then back to 1 over 500 ms. */
    public void dipAndSwap(int color, int dipMs) {
        ambient.animate().alpha(0.35f).setDuration(dipMs).withEndAction(() -> {
            setAmbient(color);
            ambient.animate().alpha(1f).setDuration(500).start();
        }).start();
    }

    /**
     * The glow used to be a full-screen radial GradientDrawable. On Android 10 that drawable is
     * drawn on the CPU, so fading it for half a second redrew 1920×1080 every frame and the
     * highlight looked like it was running at a few frames per second. A small bitmap stretched
     * over the screen is one texture. It is painted once in white and tinted with the app's colour,
     * so changing apps neither repaints it on the CPU (about 11 ms) nor uploads it again.
     */
    public void setAmbient(int color) {
        if (color == ambientColor && ambient.getBackground() != null) return;
        ambientColor = color;
        BitmapDrawable glow = (BitmapDrawable) ambient.getBackground();
        if (glow == null) {
            int w = 480;
            int h = 270;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (host.state().cinematic) {
                // Higher and stronger, from the corner away from the shelf's text: the app's colour floods
                // the top of the screen over its artwork, and the scrim keeps the text side dark.
                boolean rtl = host.isRtl();
                paint.setShader(new RadialGradient(
                        w * (rtl ? 0.20f : 0.80f), h * 0.06f, w * 0.78f,
                        new int[]{0xA6FFFFFF, 0x47FFFFFF, 0x00FFFFFF},
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP));
            } else if (Config.GLOW_HORIZON.equals(glowStyle)) {
                // The colour washes down from the top edge and fades out three quarters of the way down,
                // with a brighter band along the top like light over a horizon.
                paint.setShader(new LinearGradient(0, 0, 0, h * 0.75f,
                        new int[]{0x66FFFFFF, 0x24FFFFFF, 0x00FFFFFF},
                        new float[]{0f, 0.4f, 1f},
                        Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
                paint.setShader(new RadialGradient(
                        w * 0.5f, -h * 0.25f, w * 0.55f,
                        new int[]{0x40FFFFFF, 0x00FFFFFF},
                        null,
                        Shader.TileMode.CLAMP));
            } else {
                paint.setShader(new RadialGradient(
                        w * 0.32f, h * 0.42f, w * 0.62f,
                        new int[]{0x5CFFFFFF, 0x26FFFFFF, 0x00FFFFFF},
                        new float[]{0f, 0.45f, 1f},
                        Shader.TileMode.CLAMP));
            }
            canvas.drawRect(0, 0, w, h, paint);
            glow = new BitmapDrawable(host.activity().getResources(), bmp);
            glow.setGravity(Gravity.FILL);
            ambient.setBackground(glow);
        }
        // The default tint mode, SRC_IN, keeps the mask's alpha and takes the colour from the tint.
        glow.setTint(0xFF000000 | color);
    }

    public void transitionAmbient(int color) {
        // A rapid focus change continues from the colour actually displayed on screen.
        stopAmbientAnimation();
        ambient.animate().cancel();
        if (color == ambientColor) {
            ambient.animate().alpha(1f).setDuration(180).start();
            return;
        }
        ambient.animate().alpha(1f).setDuration(180).start();
        ambientAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), ambientColor, color);
        ambientAnimator.setDuration(360);
        ambientAnimator.addUpdateListener(animation -> setAmbient((int) animation.getAnimatedValue()));
        ambientAnimator.start();
    }

    public void stopAmbientAnimation() {
        if (ambientAnimator != null) {
            ambientAnimator.cancel();
            ambientAnimator = null;
        }
    }
}
