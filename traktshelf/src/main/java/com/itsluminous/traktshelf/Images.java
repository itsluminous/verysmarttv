package com.itsluminous.traktshelf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Posters and backdrops on disk (files/img/<kind>_<trakt>_p.jpg, _b.jpg) and a small memory cache of decoded,
 * downsampled bitmaps. The disk folder is trimmed to {@link #DISK_BUDGET} bytes by age after every sync; the memory
 * cache is 1/10 of the heap, in RGB_565.
 */
final class Images {
    private static final String TAG = "TraktShelf.Images";
    /** The whole image folder stays under this (the task's "about 150 MB" for the cache; JSON is a few hundred KB). */
    static final long DISK_BUDGET = 140L << 20;
    static final int MAX_DOWNLOAD = 3 << 20;

    private static LruCache<String, Bitmap> memory;

    private Images() {
    }

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "img");
        if (!d.isDirectory() && !d.mkdirs()) Log.w(TAG, "cannot create " + d);
        return d;
    }

    private static synchronized LruCache<String, Bitmap> memory() {
        if (memory == null) {
            int kb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 10);
            memory = new LruCache<String, Bitmap>(kb) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getAllocationByteCount() / 1024;
                }
            };
        }
        return memory;
    }

    /** Downloads url to name unless the file exists; true when the file is there afterwards. */
    static boolean fetch(Context c, String url, String name) {
        if (url == null) return false;
        File f = new File(dir(c), name);
        if (f.isFile() && f.length() > 0) return true;
        byte[] bytes = Http.getBytes(url, MAX_DOWNLOAD);
        if (bytes == null) return false;
        // Reject anything that is not a decodable image before it lands in the cache.
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
        if (o.outWidth <= 0) return false;
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(bytes);
        } catch (IOException e) {
            Log.w(TAG, "write " + name + ": " + e.getMessage());
            return false;
        }
        return tmp.renameTo(f);
    }

    static File file(Context c, String name) {
        File f = new File(dir(c), name);
        return f.isFile() ? f : null;
    }

    /** The memory cache only, for a bind on the main thread; null when the image is not decoded yet. */
    static Bitmap decodeCachedOnly(String name, int maxW) {
        return memory().get(name + "@" + maxW);
    }

    /** Decodes a cached image to about maxW x maxH (power-of-two downsampling), from memory when it was decoded before. */
    static Bitmap decode(Context c, String name, int maxW, int maxH) {
        String key = name + "@" + maxW;
        Bitmap b = memory().get(key);
        if (b != null) return b;
        File f = file(c, name);
        if (f == null) return null;
        b = decodeFile(f, maxW, maxH, Bitmap.Config.RGB_565);
        if (b != null) memory().put(key, b);
        return b;
    }

    static Bitmap decodeFile(File f, int maxW, int maxH, Bitmap.Config config) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= maxW && o.outHeight / (sample * 2) >= maxH) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        o.inPreferredConfig = config;
        try {
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (OutOfMemoryError e) {
            memory().evictAll();
            return null;
        }
    }

    /** Drops the oldest files until the folder is under budget. */
    static void trim(Context c) {
        File[] files = dir(c).listFiles();
        if (files == null) return;
        long total = 0;
        for (File f : files) total += f.length();
        if (total <= DISK_BUDGET) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File f : files) {
            if (total <= DISK_BUDGET * 9 / 10) break;
            total -= f.length();
            if (!f.delete()) Log.w(TAG, "cannot delete " + f.getName());
        }
        Log.i(TAG, "trimmed image cache to " + (total >> 20) + " MB");
    }

    static void clearMemory() {
        if (memory != null) memory.evictAll();
    }
}
