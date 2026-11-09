package com.itsluminous.traktshelf;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One row of posters. Posters decode off the main thread, one worker for the whole app. */
final class PosterAdapter extends RecyclerView.Adapter<PosterAdapter.Holder> {
    interface Listener {
        void onFocused(Item item, View card);

        void onClicked(Item item);
    }

    static final ExecutorService DECODER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "poster-decode");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final List<Item> items;
    private final Listener listener;

    PosterAdapter(List<Item> items, Listener listener) {
        this.items = items;
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).stableId();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.card, parent, false);
        Ui.applyFont(v);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Item it = items.get(position);
        h.item = it;
        h.poster.setImageBitmap(null);
        h.title.setText(it.title);
        h.title.setVisibility(View.VISIBLE);
        String coming = Ui.coming(h.coming.getContext(), it);
        h.coming.setText(coming);
        h.coming.setVisibility(coming == null ? View.GONE : View.VISIBLE);
        boolean progress = it.aired > 0 && it.completed > 0 && it.completed < it.aired;
        h.track.setVisibility(progress ? View.VISIBLE : View.GONE);
        h.bar.setVisibility(progress ? View.VISIBLE : View.GONE);
        if (progress) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) h.bar.getLayoutParams();
            int full = (int) (Ui.dp(h.bar.getContext(), 112 - 16));
            lp.width = Math.max((int) Ui.dp(h.bar.getContext(), 4), full * it.completed / it.aired);
            h.bar.setLayoutParams(lp);
        }
        final String file = it.posterFile();
        final Context c = h.poster.getContext();
        final int w = (int) Ui.dp(c, 112), hgt = (int) Ui.dp(c, 168);
        Bitmap cached = Images.decodeCachedOnly(file, w);
        if (cached != null) {
            h.poster.setImageBitmap(cached);
            h.title.setVisibility(View.GONE);
            return;
        }
        DECODER.execute(() -> {
            Bitmap b = Images.decode(c, file, w, hgt);
            if (b == null) return;
            MAIN.post(() -> {
                if (h.item != it) return;
                h.poster.setImageBitmap(b);
                h.title.setVisibility(View.GONE);
            });
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class Holder extends RecyclerView.ViewHolder {
        final ImageView poster;
        final TextView title, coming;
        final View track, bar;
        Item item;

        Holder(@NonNull View v) {
            super(v);
            poster = v.findViewById(R.id.poster);
            title = v.findViewById(R.id.cardTitle);
            coming = v.findViewById(R.id.coming);
            track = v.findViewById(R.id.progressTrack);
            bar = v.findViewById(R.id.progressBar);
            v.setClipToOutline(true);
            v.setOnFocusChangeListener((view, has) -> {
                view.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(160).start();
                if (has && item != null) listener.onFocused(item, view);
            });
            v.setOnClickListener(view -> {
                if (item != null) listener.onClicked(item);
            });
        }
    }
}
