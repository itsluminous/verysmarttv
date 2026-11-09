package com.itsluminous.verysmarttv;

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

/**
 * The theme's one wallpaper ({@code wallpaper.jpg} in {@link Config#dir}) behind Home, or the plain background
 * colour when there is none or {@code wallpaper} is false. Decoded once on the main thread in onCreate, as the
 * fork's single wallpaper always was, so Home's first frame already has it; sampled and scaled to cover the screen
 * ({@link WallpaperSize}), {@code wallpaper_dim} painted in, and uploaded as one hardware bitmap. Released under
 * memory pressure and shown again by the next onCreate.
 */
final class Wallpaper {
    private final FrameLayout root;
    private final ImageView image;
    private final int backgroundColor;
    /** config wallpaper_true_color: ARGB_8888 instead of RGB_565. */
    private final Bitmap.Config bitmapConfig;
    private final int width, height;
    /** wallpaper_dim as an ARGB black, painted into the picture once instead of a full-screen shade view. */
    private final int dimColor;
    private Bitmap current;

    Wallpaper(Context context, FrameLayout root, Config config) {
        this.root = root;
        backgroundColor = config.backgroundColor;
        bitmapConfig = config.wallpaperTrueColor ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        dimColor = config.wallpaperDim > 0 ? (config.wallpaperDim * 255 / 100) << 24 : 0;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        File file = config.wallpaper;
        if (file == null) return;
        Bitmap bitmap = decode(file);
        if (bitmap == null) return;   // unreadable or over 4K: the plain colour stays
        current = bitmap;
        image.setImageBitmap(bitmap);
        root.addView(image, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // The picture covers the whole screen: the root's plain colour under it would be overdraw.
        root.setBackground(null);
    }

    /** Drops the wallpaper texture when Home is hidden and Android asks the process to free memory. */
    void releaseForMemoryPressure() {
        if (current == null) return;
        image.setImageDrawable(null);
        // The renderer may still have a queued frame that references this hardware bitmap: drop our references and
        // let Android reclaim its buffer after rendering releases it instead of recycling it mid-frame.
        current = null;
        root.setBackgroundColor(backgroundColor);
    }

    void close() {
        current = null;
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
            // One copy in GPU memory instead of a software bitmap plus its texture.
            Bitmap gpu = decoded.copy(Bitmap.Config.HARDWARE, false);
            if (gpu == null) return decoded;
            decoded.recycle();
            return gpu;
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }
}
