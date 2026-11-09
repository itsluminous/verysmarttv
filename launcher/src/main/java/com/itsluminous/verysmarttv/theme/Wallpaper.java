package com.itsluminous.verysmarttv.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import androidx.annotation.NonNull;

/**
 * The theme's one wallpaper ({@code wallpaper.jpg} in {@link Config#dir}) behind Home, or the plain background
 * colour when there is none or {@code wallpaper} is false. Decoded once on the main thread in onCreate, as the
 * fork's single wallpaper always was, so Home's first frame already has it; sampled and scaled to cover the screen
 * ({@link WallpaperSize}), {@code wallpaper_dim} painted in, and uploaded as one hardware bitmap. Released under
 * memory pressure ({@link #releaseForMemoryPressure}) and decoded again by {@link #restore} when Home resumes, the
 * way the fork's player showed its picture again on the next start.
 */
public final class Wallpaper {
    private final FrameLayout root;
    private final ImageView image;
    private final int backgroundColor;
    /** config wallpaper_true_color: ARGB_8888 instead of RGB_565. */
    private final Bitmap.Config bitmapConfig;
    private final int width, height;
    /** wallpaper_dim as an ARGB black, painted into the picture once instead of a full-screen shade view. */
    private final int dimColor;
    /** The theme's picture, or null when there is none or it could not be decoded (the plain colour stays). */
    private final File file;
    private Bitmap current;
    private boolean closed;

    public Wallpaper(@NonNull Context context, @NonNull FrameLayout root, @NonNull Config config) {
        this(context, root, config.wallpaper, config.backgroundColor, config.wallpaperTrueColor, config.wallpaperDim);
    }

    /** The values Config carries for the wallpaper, so a test needs no Config. */
    Wallpaper(Context context, FrameLayout root, File file, int backgroundColor, boolean trueColor, int dimPercent) {
        this.root = root;
        this.backgroundColor = backgroundColor;
        bitmapConfig = trueColor ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        dimColor = dimPercent > 0 ? (dimPercent * 255 / 100) << 24 : 0;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        Bitmap bitmap = file == null ? null : decode(file);
        if (bitmap == null) {   // none, unreadable or over 4K: the plain colour stays, and restore has nothing to do
            this.file = null;
            return;
        }
        this.file = file;
        root.addView(image, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        show(bitmap);
    }

    /** True while the picture is on screen; false with no picture, or after a release that restore has not undone. */
    boolean shown() {
        return current != null;
    }

    /** Drops the wallpaper texture when Home is hidden and Android asks the process to free memory. */
    public void releaseForMemoryPressure() {
        if (current == null) return;
        image.setImageDrawable(null);
        // The renderer may still have a queued frame that references this hardware bitmap: drop our references and
        // let Android reclaim its buffer after rendering releases it instead of recycling it mid-frame.
        current = null;
        root.setBackgroundColor(backgroundColor);
    }

    /**
     * Home is back after a release: decode the picture again, on the main thread like the first one, so the frame
     * that brings Home back already has it. Nothing to do while it is on screen or there is no picture.
     */
    public void restore() {
        if (closed || current != null || file == null) return;
        Bitmap bitmap = decode(file);
        if (bitmap != null) show(bitmap);
    }

    public void close() {
        closed = true;
        current = null;
    }

    private void show(Bitmap bitmap) {
        current = bitmap;
        image.setImageBitmap(bitmap);
        // The picture covers the whole screen: the root's plain colour under it would be overdraw.
        root.setBackground(null);
    }

    private Bitmap decode(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int w = options.outWidth, h = options.outHeight;
            if (w <= 0 || h <= 0) return null;
            int sample = WallpaperSize.sampleSize(w, h, width, height);
            if (!WallpaperSize.accepted(w / sample, h / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(w / sample, h / sample, width, height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = bitmapConfig;
            options.inMutable = dimColor != 0;
            Bitmap decoded = BitmapFactory.decodeFile(file.getPath(), options);
            if (decoded == null) return null;
            if (dimColor != 0) new Canvas(decoded).drawColor(dimColor);
            return toHardware(decoded);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    /** One copy in GPU memory instead of a software bitmap plus its texture; the software bitmap where that fails. */
    private static Bitmap toHardware(Bitmap decoded) {
        try {
            Bitmap gpu = decoded.copy(Bitmap.Config.HARDWARE, false);
            if (gpu == null) return decoded;
            decoded.recycle();
            return gpu;
        } catch (RuntimeException unsupported) {
            return decoded;
        }
    }
}
