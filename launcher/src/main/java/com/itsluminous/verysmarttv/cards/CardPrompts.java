package com.itsluminous.verysmarttv.cards;

import android.app.Dialog;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itsluminous.verysmarttv.R;
import com.itsluminous.verysmarttv.home.HomeActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Module cards on Home (DESIGN.md §8, moved from the activity verbatim): the prompt that asks about a card once per
 * signing key, and the Module cards list under the Settings pill. The grid rebuild discovers the cards and hands
 * them here; a decision rebuilds the tiles through the host.
 */
public final class CardPrompts {
    private final HomeActivity host;
    private final FrameLayout root;
    private List<Cards.Card> cards = Collections.emptyList();
    private Dialog cardPrompt;
    /** Set when a decision could not be saved: no more prompts until the next resume, instead of asking in a loop. */
    private boolean promptsPaused;

    public CardPrompts(@NonNull HomeActivity host, @NonNull FrameLayout root) {
        this.host = host;
        this.root = root;
    }

    /**
     * The cards found by the last tile rebuild (reloadTiles): every step into the card code runs under CardFeature,
     * so a failure leaves the ordinary tiles and keeps cards off for this process.
     */
    @NonNull
    public List<Cards.Card> discover(@NonNull Set<String> hiddenApps, @NonNull List<ResolveInfo> leanback) {
        final List<Cards.Card> none = Collections.emptyList();
        cards = host.state().moduleCards ? CardFeature.run(() -> Cards.discover(host, hiddenApps,
                () -> Cards.load(Cards.storeFile(host)), leanback), none) : none;
        return cards.isEmpty() ? none : CardFeature.run(() -> Cards.shown(cards), none);
    }

    /** onResume: a prompt whose decision could not be saved may ask again. */
    public void onResume() {
        promptsPaused = false;
    }

    /** onPause: nothing of ours stays on screen over another app; an undecided prompt simply returns next time. */
    public void onPause() {
        if (cardPrompt != null) cardPrompt.dismiss();
    }

    /** A prompt is up: keys belong to it. */
    public boolean promptShowing() {
        return cardPrompt != null;
    }

    /**
     * Asks about the next card that has no decision for its current signing key. One prompt at a
     * time, only over a laid out grid with window focus (not under a popup or another app), and
     * the next one follows as soon as this one is answered. Nothing is shown until the owner says Show.
     */
    public void showCardPrompt() {
        if (!host.state().moduleCards || !host.state().tilesReady || promptsPaused || cardPrompt != null || !host.activity().hasWindowFocus() || host.activity().isFinishing()
                || host.reordering()) return;
        if (cards.isEmpty() || !CardFeature.working()) return;
        CardFeature.run(() -> {
            showCardPromptNow();
            return null;
        }, null);
    }

    private void showCardPromptNow() {
        final Cards.Card card = Cards.firstPending(cards);
        if (card == null) return;

        View panel = host.activity().getLayoutInflater().inflate(R.layout.dialog_card_prompt, root, false);
        ((TextView) panel.findViewById(R.id.title)).setText(host.activity().getString(R.string.card_prompt_title, card.appLabel));
        ((TextView) panel.findViewById(R.id.body)).setText(card.state == Cards.State.RESIGNED
                ? host.activity().getString(R.string.card_prompt_resigned, card.appLabel)
                : card.state == Cards.State.RELABELLED
                ? host.activity().getString(R.string.card_prompt_relabelled, card.appLabel)
                : host.activity().getString(R.string.card_prompt_body, card.appLabel));
        TextView names = panel.findViewById(R.id.names);
        if (Cards.fold(card.label).equals(Cards.fold(card.appLabel))) names.setVisibility(View.GONE);
        else names.setText(host.activity().getString(R.string.card_prompt_names, card.label, card.appLabel));
        ((TextView) panel.findViewById(R.id.identity)).setText(
                host.activity().getString(R.string.card_prompt_identity, card.pkg, Cards.shortDigest(card.digest)));
        TextView keep = panel.findViewById(R.id.btnKeep);
        TextView show = panel.findViewById(R.id.btnShow);
        boolean rtl = host.isRtl();   // the framework mirrors the two buttons; the key guard names the visual ends

        final Dialog dialog = new Dialog(host.activity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(false);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (440 * host.density()), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.6f);
        }
        // One decision per prompt: the buttons and Back (cancel) all end here.
        final boolean[] decided = {false};
        View.OnClickListener choose = v -> {
            if (decided[0]) return;
            decided[0] = true;
            cardPrompt = null;
            dialog.dismiss();
            decideCard(card, v.getId() == R.id.btnShow);   // guards its own card calls; the tile reload stays outside
            host.restoreFocus();   // the tiles changed under the prompt; this also chains the next prompt
        };
        keep.setOnClickListener(choose);
        show.setOnClickListener(choose);
        dialog.setOnCancelListener(d -> choose.onClick(keep));
        dialog.setOnDismissListener(d -> {
            if (cardPrompt == dialog) cardPrompt = null;
        });
        // Left and Right move between the two buttons; Up and Down stay put; the ends don't wrap.
        View.OnKeyListener keys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT)
                return v.getId() == (rtl ? R.id.btnShow : R.id.btnKeep);
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    && v.getId() == (rtl ? R.id.btnKeep : R.id.btnShow);
        };
        keep.setOnKeyListener(keys);
        show.setOnKeyListener(keys);
        cardPrompt = dialog;
        dialog.show();
        keep.requestFocus();
    }

    /** Writes the owner's decision and rebuilds the tiles; focus is left where it is. */
    private void decideCard(Cards.Card card, boolean show) {
        boolean saved = CardFeature.run(() -> Cards.decide(Cards.storeFile(host.activity()), card, show), false);
        if (!saved) {
            Toast.makeText(host.activity(), R.string.card_not_saved, Toast.LENGTH_SHORT).show();
            promptsPaused = true;
        }
        host.reloadTiles();
    }

    /** Dropdown under the Settings pill (hold OK): every discovered card, its package and key; OK toggles Shown and Hidden. */
    public void showModuleCards() {
        if (cards.isEmpty() || !CardFeature.working()) {
            Toast.makeText(host.activity(), R.string.no_module_cards, Toast.LENGTH_SHORT).show();
            return;
        }
        CardFeature.run(() -> {
            showModuleCardsNow();
            return null;
        }, null);
    }

    private void showModuleCardsNow() {
        View anchor = host.findViewById(R.id.btnSettings);
        View panel = host.activity().getLayoutInflater().inflate(R.layout.popup_cards, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final PopupWindow popup = host.popups().glassPopup(panel, (int) (400 * host.density()));
        List<Cards.Card> listed = new ArrayList<>(cards);
        Collections.sort(listed, Cards::byLabel);
        for (Cards.Card card : listed) {
            View row = host.activity().getLayoutInflater().inflate(R.layout.item_card_row, rows, false);
            row.setTag(card.pkg);
            ((TextView) row.findViewById(R.id.title)).setText(card.label);
            TextView app = row.findViewById(R.id.app);
            if (Cards.fold(card.label).equals(Cards.fold(card.appLabel))) app.setVisibility(View.GONE);
            else app.setText(host.activity().getString(R.string.card_app_name, card.appLabel));
            ((TextView) row.findViewById(R.id.pkg)).setText(card.pkg);
            ((TextView) row.findViewById(R.id.digest)).setText(Cards.longDigest(card.digest));
            final String pkg = card.pkg;
            row.setOnClickListener(v -> CardFeature.run(() -> {
                // The list stays open; the decision is for the card as it is now, not as it was listed.
                Cards.Card now = cardFor(pkg);
                if (now == null) return null;
                decideCard(now, now.state != Cards.State.SHOWN);
                // Every row, not only this one: a toggle can move another card across the limit of 8.
                updateCardStates(rows);
                return null;
            }, null));
            rows.addView(row);
        }
        updateCardStates(rows);
        // Scroll inside the panel instead of running off the bottom of a 720p screen.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] at = new int[2];
        anchor.getLocationInWindow(at);
        int room = root.getHeight() - (at[1] + anchor.getHeight()) - (int) (34 * host.density());
        popup.setHeight(Math.min(panel.getMeasuredHeight(), Math.max(room, (int) (120 * host.density()))));
        host.popups().dropIn(popup, panel, anchor, Gravity.END);
        rows.getChildAt(0).requestFocus();
    }

    private Cards.Card cardFor(String pkg) {
        for (Cards.Card c : cards) if (c.pkg.equals(pkg)) return c;
        return null;
    }

    /** Writes the state of every row in the Module cards list from the current card list. */
    private void updateCardStates(LinearLayout rows) {
        List<Cards.Card> drawn = Cards.shown(cards);
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            Cards.Card card = row.getTag() instanceof String ? cardFor((String) row.getTag()) : null;
            ((TextView) row.findViewById(R.id.state)).setText(card != null ? stateText(card, drawn.contains(card)) : "");
        }
    }

    /** The row's state: a card the owner set to Shown but which is beyond the eight drawn says so. */
    private String stateText(Cards.Card card, boolean drawn) {
        switch (card.state) {
            case SHOWN:
                return host.activity().getString(drawn ? R.string.card_state_shown : R.string.card_state_over_limit);
            case HIDDEN:
                return host.activity().getString(R.string.card_state_hidden);
            case RESIGNED:
                return host.activity().getString(R.string.card_state_new_key);
            case RELABELLED:
                return host.activity().getString(R.string.card_state_new_label);
            default:
                return host.activity().getString(R.string.card_state_new);
        }
    }
}
