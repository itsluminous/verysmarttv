package com.itsluminous.verysmarttv.topbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.itsluminous.verysmarttv.R;
import com.itsluminous.verysmarttv.clock.ClockRefresh;
import com.itsluminous.verysmarttv.grid.LaunchActions;
import com.itsluminous.verysmarttv.home.HomeActivity;
import com.itsluminous.verysmarttv.inputs.CoocaaSources;
import com.itsluminous.verysmarttv.settings.SystemSettings;
import com.itsluminous.verysmarttv.theme.Config;
import com.itsluminous.verysmarttv.theme.Motion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The header and the pills (DESIGN.md §8, moved from the activity verbatim): the greeting, the clock and date and
 * their layouts, the Inputs, network, VPN, reorder, library and Settings pills with their keys and labels, the
 * top_bar_apps buttons, and the callbacks that keep the network and Inputs pills current. The grid asks it for the
 * bar's first button; the carousel for the pills' dark base over its art.
 */
public final class TopBarController {
    // Not private: the anonymous callbacks and receivers below read these, and an accessor per read is what lint's
    // SyntheticAccessor flags (the fork baselined them in the activity; this file has none).
    final HomeActivity host;
    final FrameLayout root;
    private static final long MINUTE_MS = 60_000L;


    /**
     * 2025-01-01. TVs without a clock battery boot at the epoch (08:00 in China) and keep it until network
     * time arrives; the clock, date and greeting stay hidden before then.
     */
    private static final long TIME_VALID_AFTER_MS = 1_735_689_600_000L;

    private TextView greeting;

    private View clockBlock;

    private View btnInputs;

    /** config show_inputs_pill: false keeps the Inputs pill hidden even when the TV has host.inputs(). */
    private boolean showInputsPill = true;

    private TextView btnWifi;

    /** The network pill's icon as last set, so a refresh that changes nothing does not lay the top bar out again. */
    private int wifiIcon;

    /** The pills have been filled in once; later resumes refresh them after the first frame. */
    private boolean pillsShown;

    /** greeting_name: said after the greeting, which is shown in capitals. */
    private String greetingName = "";

    private String vpnApp;

    /** vpn_pill: the VPN shown as its own pill (btnVpn) instead of a badge in the network pill. Off with top_menu. */
    private boolean vpnPill;

    private TextView btnVpn;

    /** The VpnBadge pill state btnVpn shows, so a refresh that changes nothing does not restyle it. */
    private int vpnPillState = VpnBadge.PILL_HIDDEN;

    /** icon_only_vpn: the VPN pill is a round lock button; off, it keeps the plain glass look saved here. */
    private boolean iconOnlyVpn;

    private Drawable.ConstantState vpnPlainBackground;

    private ColorStateList vpnPlainInk;

    private int greetingColor;




    private View btnReorder;

    /** Refreshes the network pill when the connection changes: at boot Wi-Fi often connects after Home is shown. */
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            host.handler().post(TopBarController.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            host.handler().post(TopBarController.this::updateWifiLabel);
        }

        /** wifi_status_warnings follows validation; signal-strength updates also land here, so skip those. */
        private boolean lastValidated;

        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities caps) {
            boolean validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            // open_on_start_network_wait: the boot's app opens on the first validated network.
            host.startup().onNetworkValidated();
            if (validated == lastValidated) return;
            lastValidated = validated;
            if (wifiNoInternetTint != null) host.handler().post(TopBarController.this::updateWifiLabel);
        }
    };

    /** Shows the Inputs pill once the TV registers an input: at boot HDMI inputs can appear after Home is shown. */
    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputAdded(String inputId) {
            updateInputsButton();
        }

        @Override
        public void onInputRemoved(String inputId) {
            updateInputsButton();
        }
    };

    /** Listens for any VPN coming up or going down, even one this app's traffic is excluded from. */
    private final ConnectivityManager.NetworkCallback vpnCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            host.handler().post(TopBarController.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            host.handler().post(TopBarController.this::updateWifiLabel);
        }
    };

    // Right after boot the clock is often wrong until network time arrives; don't wait a minute to catch up.
    private final IntentFilter timeFilter = new IntentFilter(Intent.ACTION_TIME_CHANGED);

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClockVisibility();
            clockRefresh.start();   // the wall clock was set: line the redraw up with the new minute
        }
    };

    /** Keeps the Home clocks moving on Android before 12 when the firmware stops ACTION_TIME_TICK (see ClockRefresh). */
    ClockRefresh clockRefresh;

    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            host.handler().postDelayed(this, MINUTE_MS);
        }
    };

    /**
     * The top-bar pills over the carousel's backdrop: the same frost over a dark base (pill_bg_over_art), so their white
     * icons read over a bright sky as well as over the wallpaper; back to pill_bg when no backdrop is behind them. The
     * VPN pill is left alone, its own drawable is dark glass already. The padding is kept across the swap.
     */
    public void setPillsOverArt(boolean on) {
        if (host.state().pillsOverArt == on) return;
        host.state().pillsOverArt = on;
        int bg = on ? R.drawable.pill_bg_over_art : R.drawable.pill_bg;
        for (View pill : new View[]{btnInputs, btnWifi, btnReorder, btnLibraryTop, host.findViewById(R.id.btnSettings)}) {
            if (pill == null) continue;
            int l = pill.getPaddingLeft(), t = pill.getPaddingTop(), r = pill.getPaddingRight(), b = pill.getPaddingBottom();
            pill.setBackgroundResource(bg);
            pill.setPadding(l, t, r, b);
        }
    }

    /**
     * date_two_lines: the day of the week over the day and month, each in the TV's language's own pattern
     * (DateFormat.getBestDateTimePattern, so "4 NOVEMBER" here and "NOVEMBER 4" in the US), in the date's caps and
     * tracking. The block is centred on the clock by translation, whatever the header's own alignment, so the
     * header's height does not move: two 14sp lines are shorter than the clock.
     */
    private void dateOnTwoLines() {
        TextClock date = host.findViewById(R.id.date);
        Locale locale = host.getResources().getConfiguration().getLocales().get(0);
        String format = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEE") + "\n"
                + android.text.format.DateFormat.getBestDateTimePattern(locale, "d MMMM");
        date.setSingleLine(false);
        date.setMaxLines(2);
        date.setLineSpacing(6 * host.density(), 1f);
        date.setFormat12Hour(format);
        date.setFormat24Hour(format);
        View column = (View) date.getParent();
        View clock = host.findViewById(R.id.clock);
        ViewGroup row = (ViewGroup) column.getParent();
        View.OnLayoutChangeListener centre = (v, l, t, r, b, ol, ot, or, ob) -> {
            Rect c = scratch;
            c.set(0, 0, clock.getWidth(), clock.getHeight());
            row.offsetDescendantRectToMyCoords(clock, c);
            column.setTranslationY(c.exactCenterY() - (column.getTop() + column.getHeight() / 2f));
        };
        column.addOnLayoutChangeListener(centre);
        clock.addOnLayoutChangeListener(centre);
    }

    /**
     * date_below_clock: the date column moves to its own line under the clock, in the vertical LinearLayout that holds
     * the clock's row, and the line between them goes. The date and the clock's row sit at the start.
     */
    private void moveDateBelowClock() {
        View column = (View) host.findViewById(R.id.date).getParent();
        View clockRow = (View) host.findViewById(R.id.clock).getParent();
        LinearLayout clockColumn = (LinearLayout) clockRow.getParent();
        ((ViewGroup) column.getParent()).removeView(column);
        host.findViewById(R.id.clockDivider).setVisibility(View.GONE);
        // Its own width, not the column's: in Hebrew the column is only as wide as the clock.
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(4 * host.density());
        clockColumn.addView(column, clockColumn.indexOfChild(clockRow) + 1, lp);
        // The column now wraps its texts, so they must wrap too: a match_parent TextView does not ask for a new
        // layout when its text gets longer, and the column kept the width of the day before ("TUESDAY 27 OCTOB…").
        ViewGroup texts = (ViewGroup) column;
        for (int i = 0; i < texts.getChildCount(); i++) {
            View text = texts.getChildAt(i);
            LinearLayout.LayoutParams textLp = (LinearLayout.LayoutParams) text.getLayoutParams();
            textLp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            // Keep each line where its own gravity put it (right in Hebrew) now that it no longer fills the column.
            textLp.gravity = ((TextView) text).getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK;
            text.setLayoutParams(textLp);
        }
    }

    /** Centre the one-row header when the greeting and extra date lines are absent (#2265). */
    private void centerCompactHeader() {
        LinearLayout clockRow = (LinearLayout) host.findViewById(R.id.clock).getParent();
        clockRow.setGravity(Gravity.CENTER_VERTICAL);
        clockRow.setBaselineAligned(false);
        View[] columns = {host.findViewById(R.id.clockDivider), (View) host.findViewById(R.id.date).getParent()};
        for (View column : columns) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) column.getLayoutParams();
            lp.gravity = Gravity.CENTER_VERTICAL;
            lp.bottomMargin = 0;
            column.setLayoutParams(lp);
        }
        LinearLayout buttons = host.findViewById(R.id.topButtons);
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) buttons.getLayoutParams();
        lp.addRule(RelativeLayout.ALIGN_TOP, R.id.leftBlock);
        lp.bottomMargin = 0;
        buttons.setLayoutParams(lp);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
    }

    /** With the date below, keep the icons beside the clock rather than beside the last date line. */
    private void alignDateBelowIconsWithClock() {
        View clock = host.findViewById(R.id.clock);
        View clockRow = (View) clock.getParent();
        LinearLayout buttons = host.findViewById(R.id.topButtons);
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) buttons.getLayoutParams();
        lp.addRule(RelativeLayout.ALIGN_TOP, R.id.leftBlock);
        lp.removeRule(RelativeLayout.ALIGN_BOTTOM);
        lp.topMargin = 0;
        lp.bottomMargin = 0;
        buttons.setLayoutParams(lp);
        // Either side can be laid out first; update after both without requesting another layout mid-pass.
        Runnable align = () -> buttons.setTranslationY(clockRow.getTop() + clock.getTop()
                + (clock.getHeight() - buttons.getHeight()) / 2f);
        clockRow.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> align.run());
        buttons.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> align.run());
    }

    /** The first top-bar button after Inputs that is shown, in bar order (top_bar_apps, the network pill, ...); Settings is always there. */
    @NonNull
    public View firstTopButton() {
        ViewGroup bar = host.findViewById(R.id.topButtons);
        // Bar order is child order: the framework mirrors it for a right-to-left locale (supportsRtl).
        int n = bar.getChildCount();
        for (int k = 0; k < n; k++) {
            View button = bar.getChildAt(k);
            if (button != btnInputs && button.getVisibility() == View.VISIBLE) return button;
        }
        return host.findViewById(R.id.btnSettings);
    }

    /** top_bar_apps buttons, in bar order. */
    private final List<View> topAppButtons = new ArrayList<>();

    /**
     * Puts each top_bar_apps app in the top bar as a round pill with the app's own logo, left of the network pill.
     * The logo keeps its colours: the pill style's tint, which turns the other icons white, is cleared.
     */
    private void addTopBarApps(List<String> packages, Map<String, String> launchActions) {
        ViewGroup bar = host.findViewById(R.id.topButtons);
        PackageManager pm = host.getPackageManager();
        int size = Math.round(22 * host.density());
        for (String pkg : packages) {
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) continue;   // not installed, or disabled
            // Use the same configured launch_actions as tiles; retain the normal entry if unavailable.
            launch = LaunchActions.select(pkg, launchActions, launch, (targetPackage, action) -> {
                Intent custom = LaunchActions.intent(targetPackage, action);
                return pm.resolveActivity(custom, 0) != null ? custom : null;
            });
            Drawable logo;
            CharSequence label;
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                logo = info.loadIcon(pm);
                label = info.loadLabel(pm);
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }
            TextView button = (TextView) host.getLayoutInflater().inflate(R.layout.top_app_button, bar, false);
            // pillKeys finds the bar's ends by id: without one of its own, every app button would be an end.
            button.setId(View.generateViewId());
            logo.setBounds(0, 0, size, size);
            button.setCompoundDrawablesRelative(logo, null, null, null);
            button.setCompoundDrawableTintList(null);
            button.setContentDescription(label);
            Intent target = launch;
            button.setOnClickListener(v -> host.open(new Intent(target)));
            // After the network pill and the VPN pill, in bar order.
            bar.addView(button, bar.indexOfChild(btnWifi));
            topAppButtons.add(button);
        }
    }

    /** Some TVs (Coocaa OS) set the network time without ACTION_TIME_CHANGED, so keep checking until it is valid. */
    private final Runnable clockCheck = this::updateClockVisibility;

    public void updateClockVisibility() {
        boolean valid = System.currentTimeMillis() >= TIME_VALID_AFTER_MS;
        clockBlock.setVisibility(valid ? View.VISIBLE : View.INVISIBLE);
        host.handler().removeCallbacks(clockCheck);
        if (valid) {
            updateGreeting();
        } else {
            host.handler().postDelayed(clockCheck, 1_000L);
        }
    }

    public void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        // Locale-specific day parts (app language on Android 13+).
        // Indonesian: Pagi 00-10, Siang 10-15, Sore 15-18, Malam 18-24.
        // Spain (es-ES) and Catalan: "Buenas tardes" into the evening; "Buenas noches" from 21.
        // Latin American Spanish (es-MX, es-AR, …) keeps the default hours: evening strings are also
        // «Buenas noches», and there «Buenas noches» from ~17 matches local use better than Spain's 21.
        Locale locale = host.getResources().getConfiguration().getLocales().get(0);
        String lang = locale.getLanguage();
        String country = locale.getCountry();
        if ("in".equals(lang) || "id".equals(lang)) {
            if (hour < 10) res = R.string.good_morning;
            else if (hour < 15) res = R.string.good_afternoon;
            else if (hour < 18) res = R.string.good_evening;
            else res = R.string.good_night;
        } else if (("es".equals(lang) && "ES".equals(country)) || "ca".equals(lang)) {
            if (hour < 6) res = R.string.good_night;
            else if (hour < 12) res = R.string.good_morning;
            else if (hour < 21) res = R.string.good_afternoon;
            else res = R.string.good_night;
        } else if ("da".equals(lang)) {
            // Danish day parts: "God morgen" until 11, then "God formiddag" to noon.
            if (hour < 5) res = R.string.good_night;
            else if (hour < 11) res = R.string.good_morning;
            else if (hour < 12) res = R.string.good_forenoon;
            else if (hour < 18) res = R.string.good_afternoon;
            else if (hour < 22) res = R.string.good_evening;
            else res = R.string.good_night;
        } else {
            if (hour < 5) res = R.string.good_night;
            else if (hour < 12) res = R.string.good_morning;
            else if (hour < 17) res = R.string.good_afternoon;
            else if (hour < 22) res = R.string.good_evening;
            else res = R.string.good_night;
        }
        greeting.setText(greetingName.isEmpty() ? host.getString(res)
                : host.getString(R.string.greeting_with_name, host.getString(res), greetingName));
        greeting.setTextColor(greetingColor);
    }

    private void refreshPillsAfterNextDraw() {
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                root.getViewTreeObserver().removeOnPreDrawListener(this);
                // Posted from inside the frame's traversal, so it runs after that frame is drawn. A post straight
                // from onResume would run first: the frame is only scheduled after onResume returns.
                host.handler().post(() -> {
                    if (host.isDestroyed()) return;
                    updateWifiLabel();
                    updateInputsButton();
                });
                return true;
            }
        });
        root.invalidate();   // a resume that changes nothing on screen still draws once, so the refresh runs
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    @SuppressWarnings("deprecation")
    public void updateWifiLabel() {
        String text = host.getString(R.string.wifi);
        boolean vpn = false, online = false, validated = false;
        int icon = R.drawable.ic_wifi;
        try {
            ConnectivityManager cm = (ConnectivityManager) host.getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean wifi = false, ethernet = false;
            // All networks, not just the active one: with a VPN up the active network is the VPN itself.
            for (Network net : cm != null ? cm.getAllNetworks() : new Network[0]) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps == null) continue;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpn = true;
                    continue;
                }
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                online = true;
                // Android reached the internet through it; a captive portal stays unvalidated until login.
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) validated = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true;
            }
            if (!online) {
                text = host.getString(R.string.offline);
                vpn = false;
            } else if (ethernet) {
                text = host.getString(R.string.ethernet);
                icon = R.drawable.ic_ethernet;
            } else if (wifi) {
                WifiManager wm = (WifiManager) host.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        // With a VPN app configured, a VPN that is down matters too: show a red "VPN off" capsule then.
        boolean vpnDown = !vpn && online && !vpnApp.isEmpty();
        if (vpnPill) updateVpnPill(vpn);
        if ((vpn || vpnDown) && !vpnPill) {
            // Network name, then the green lock-and-VPN (or red "VPN off") capsule drawn in place of the word.
            String badge = host.getString(vpn ? R.string.vpn : R.string.vpn_off);
            VpnBadge span = new VpnBadge(vpn ? host.getDrawable(R.drawable.ic_lock) : null, host.density());
            // The pill has a maximum width and ellipsizes at the end, which would cut the badge off after
            // a long network name, so shorten the name to whatever room the badge leaves. The Wi-Fi icon and its
            // gap are counted by hand: until the pill has a resolved layout direction (the first call, from
            // onResume, runs before that) getTotalPaddingStart/End leave the compound drawables out, which
            // left the name about 31dp too much room and the badge cut off.
            TextPaint paint = btnWifi.getPaint();
            Drawable[] compound = btnWifi.getCompoundDrawablesRelative();
            int iconRoom = compound[0] != null ? compound[0].getIntrinsicWidth() + btnWifi.getCompoundDrawablePadding() : 0;
            float room = btnWifi.getMaxWidth() - btnWifi.getPaddingStart() - btnWifi.getPaddingEnd() - iconRoom
                    - span.getSize(paint, badge, 0, badge.length(), null) - paint.measureText(" ") - 2 * host.density();
            String name = TextUtils.ellipsize(text, paint, Math.max(0, room), TextUtils.TruncateAt.END).toString();
            // A long badge ("VPN отключён") can leave room for a stub like "MT…", which says nothing and, after
            // rounding, can push the badge itself out of the pill. Show the badge alone then. On Ethernet the
            // icon already names the link, and "Ethernet" cut to "Ethe…" or not depended on the font size (#961),
            // so Ethernet always shows the icon and the badge alone.
            // The space stays even then: a line holding only the span gets no font metrics and collapses.
            boolean stub = name.length() < text.length() && name.length() < 5;
            text = stub || icon == R.drawable.ic_ethernet ? "" : name;
            SpannableString label = new SpannableString(text + " " + badge);
            label.setSpan(span, text.length() + 1, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (!TextUtils.equals(btnWifi.getText(), label)) btnWifi.setText(label);
        } else if (!TextUtils.equals(btnWifi.getText(), text)) {
            btnWifi.setText(text);
        }
        if (wifiIconOnly) {
            btnWifi.setContentDescription(btnWifi.getText());
            // With pill_labels_on_focus the focused pill is showing its name: leave the fresh one on screen.
            if (!(pillLabelsOnFocus && btnWifi.hasFocus())) btnWifi.setText(null);
        }
        {
            if (icon != wifiIcon) {
                wifiIcon = icon;
                btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
            }
            ColorStateList tint = online ? wifiOnlineTint : wifiPlainTint;
            // wifi_status_warnings: orange while connected but not validated (a captive portal), red while offline.
            if (wifiNoInternetTint != null) tint = !online ? wifiOfflineTint : validated ? wifiOnlineTint : wifiNoInternetTint;
            if (wifiOnlineTint != null && btnWifi.getCompoundDrawableTintList() != tint) {
                btnWifi.setCompoundDrawableTintList(tint);
            }
        }
    }

    /** config.xml icon_only_buttons. */
    private boolean iconOnlyButtons;

    /** show_inputs_pill as configured: top_menu clears showInputsPill, so its Inputs row reads this. */
    private boolean showInputsMenuRow = true;

    private boolean launcherSettingsButton;

    /** icon_only_buttons without wifi_label_width: the Wi-Fi pill shows no network name. */
    private boolean wifiIconOnly;

    private boolean pillLabelsOnFocus;

    /** The app library button in the top bar (library_button). */
    private View btnLibraryTop;

    /** Turns a labelled top pill into the round TopButtonIcon shape: no text, the icon centred by even padding. */
    private void makeIconOnly(TextView pill) {
        pill.setContentDescription(pill.getText());
        pill.setText(null);
        pill.setMaxWidth(Integer.MAX_VALUE);
        int pad = Math.round(10 * host.density());
        pill.setPaddingRelative(pad, 0, pad, 0);
        pill.setCompoundDrawablePadding(0);
        pill.getLayoutParams().width = Math.round(42 * host.density());
    }

    /**
     * pill_labels_on_focus: an icon-only pill shows its name (kept in its contentDescription by makeIconOnly, and
     * by updateWifiLabel for the network pill) while it has focus, with the padding of a labelled pill, and goes
     * back to the 42dp circle when focus leaves. The layout pass is the TextView's own; nothing is animated, so
     * reduced motion needs no case here.
     */
    private void labelOnFocus(TextView pill) {
        if (pill == null) return;
        View.OnFocusChangeListener previous = pill.getOnFocusChangeListener();
        pill.setOnFocusChangeListener((v, focused) -> {
            if (previous != null) previous.onFocusChange(v, focused);
            ViewGroup.LayoutParams lp = pill.getLayoutParams();
            if (focused) {
                CharSequence name = pill.getContentDescription();
                if (TextUtils.isEmpty(name)) return;
                pill.setText(name);
                int side = Math.round(16 * host.density());
                pill.setPaddingRelative(side, 0, side, 0);
                pill.setCompoundDrawablePadding(Math.round(8 * host.density()));
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            } else {
                pill.setText(null);
                int pad = Math.round(10 * host.density());
                pill.setPaddingRelative(pad, 0, pad, 0);
                pill.setCompoundDrawablePadding(0);
                lp.width = Math.round(42 * host.density());
            }
            pill.setLayoutParams(lp);
        });
    }

    /**
     * The network pill opens the VPN app when one is configured and installed, otherwise Wi-Fi settings. With
     * vpn_pill the VPN app has its own pill, so this one always opens Wi-Fi settings.
     */
    public void openNetworkPill() {
        Intent i = vpnPill ? null : vpnAppIntent();
        host.open(i != null ? i : SystemSettings.intent(host, Settings.ACTION_WIFI_SETTINGS));
    }

    /** vpn_app's launch intent, or null when none is set or it is not installed. */
    private Intent vpnAppIntent() {
        if (vpnApp.isEmpty()) return null;
        Intent i = host.getPackageManager().getLeanbackLaunchIntentForPackage(vpnApp);
        return i != null ? i : host.getPackageManager().getLaunchIntentForPackage(vpnApp);
    }

    /**
     * vpn_pill: the VPN app, where it is switched on and off. Without one installed, the App library to pick from, or
     * Android's VPN settings when the theme turned the library off.
     */
    private void openVpnPill() {
        Intent i = vpnAppIntent();
        if (i != null || !host.state().appLibraryEnabled) {
            host.open(i != null ? i : SystemSettings.intent(host, Settings.ACTION_VPN_SETTINGS));
            return;
        }
        host.grid().openLibraryFromVpnPill();
    }

    /** vpn_pill: the pill outlined in green with a lock while a VPN is up, outlined in red "VPN off" while it is not;
     *  solid with a white ring only while focused. */
    private void updateVpnPill(boolean vpn) {
        int state = VpnBadge.pillState(vpn, vpnAppIntent() != null);
        if (state == VpnBadge.PILL_HIDDEN) {
            if (btnVpn.getVisibility() == View.GONE) return;
            boolean focused = btnVpn.hasFocus();
            btnVpn.setVisibility(View.GONE);
            if (focused) (btnWifi.getVisibility() == View.VISIBLE ? btnWifi : firstTopButton()).requestFocus();
            return;
        }
        if (state != vpnPillState) {
            vpnPillState = state;
            boolean up = state == VpnBadge.PILL_UP;
            // The state colour at rest (the pill is an outline then), the contrasting ink on the solid focused fill.
            ColorStateList ink = new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_focused}, new int[0]},
                    new int[]{VpnBadge.pillInkColor(up, true), VpnBadge.pillInkColor(up, false)});
            if (iconOnlyVpn) {
                // An open lock on the plain glass button, or the closed lock outlined in green like the pill (solid
                // while focused); the words go to talkback.
                btnVpn.setContentDescription(host.getString(up ? R.string.vpn : R.string.vpn_off));
                btnVpn.setBackground(up ? VpnBadge.pillBackground(true, host.density()) : vpnPlainBackground.newDrawable());
                btnVpn.setCompoundDrawablesRelativeWithIntrinsicBounds(VpnBadge.iconOnlyLock(up), 0, 0, 0);
                btnVpn.setCompoundDrawableTintList(up ? ink : vpnPlainInk);
                btnVpn.setVisibility(View.VISIBLE);
                return;
            }
            btnVpn.setText(up ? R.string.vpn : R.string.vpn_off);
            btnVpn.setTextColor(ink);
            btnVpn.setBackground(VpnBadge.pillBackground(up, host.density()));
            btnVpn.setCompoundDrawablesRelativeWithIntrinsicBounds(up ? R.drawable.ic_lock : 0, 0, 0, 0);
            btnVpn.setCompoundDrawableTintList(ink);
        }
        btnVpn.setVisibility(View.VISIBLE);
    }

    /** Hides the Inputs pill when it would have nothing to list: streaming boxes have no HDMI-in, AV or tuner. */
    public void updateInputsButton() {
        if (!showInputsPill) {
            btnInputs.setVisibility(View.GONE);
            return;
        }
        boolean any = host.inputs().liveTvIntent() != null || host.inputs().hasSourcePanel()
                || (host.inputs().coocaaSources() && !CoocaaSources.list(host).isEmpty());
        TvInputManager tim = (TvInputManager) host.getSystemService(Context.TV_INPUT_SERVICE);
        if (!any && tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                // Inputs hidden in Settings > Inputs are left out of the menu, so they don't count here either.
                if (info.isPassthroughInput() && !info.isHidden(host)) {
                    any = true;
                    break;
                }
            }
        }
        btnInputs.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    /** config.xml wifi_status_color: the Wi-Fi icon's green while online and its usual pill colours; null while off. */
    private ColorStateList wifiOnlineTint, wifiPlainTint;
    /** wifi_status_warnings (with wifi_status_color): orange without internet, red offline; null while off. */
    ColorStateList wifiNoInternetTint, wifiOfflineTint;
    /** Scratch rect for the date column's centring (dateOnTwoLines). */
    private final Rect scratch = new Rect();

    public TopBarController(@NonNull HomeActivity host, @NonNull FrameLayout root) {
        this.host = host;
        this.root = root;
    }

    /** onCreate, before the grid: the greeting, the clock block and the date layouts. */
    public void setUpHeader(@NonNull Config cfg) {
        greeting = host.findViewById(R.id.greeting);
        clockBlock = host.findViewById(R.id.leftBlock);
        clockRefresh = new ClockRefresh(clockBlock);
        greetingColor = greeting.getCurrentTextColor();
        if (!cfg.showGreeting) greeting.setVisibility(View.GONE);
        // Keep the other header layouts as designed: their extra lines change the block's centre.
        if (!cfg.showGreeting && !cfg.dateBelowClock && !cfg.topMenu) {
            centerCompactHeader();
        }
        if (cfg.headerWhite) {
            // The clock's own white for the date and the line before it, instead of their dimmed whites.
            ((TextView) host.findViewById(R.id.date)).setTextColor(Color.WHITE);
            host.findViewById(R.id.clockDivider).setBackgroundColor(Color.WHITE);
        }
        btnWifi = host.findViewById(R.id.btnWifi);
        greetingName = cfg.greetingName;
        if (cfg.dateBelowClock) moveDateBelowClock();
        if (cfg.dateBelowClock && cfg.iconOnlyButtons && !cfg.topMenu) alignDateBelowIconsWithClock();
        // date_two_lines: not with the date under the clock, which would push the header down.
        if (cfg.dateTwoLines && !cfg.dateBelowClock) dateOnTwoLines();
        // widgets (Config.widgets): the RAM and Storage bars land here in Phase 3 (DESIGN.md §9.1); the key is read
        // and the Settings row toggles it, and nothing is drawn yet.
        vpnApp = cfg.vpnApp;
        vpnPill = cfg.vpnPill && !cfg.topMenu;
        btnVpn = host.findViewById(R.id.btnVpn);
    }

    /** onCreate, after the grid: the pills, their clicks, keys and labels, and the top_bar_apps buttons. */
    public void setUpPills(@NonNull Config cfg) {
        btnInputs = host.findViewById(R.id.btnInputs);
        View btnSettings = host.findViewById(R.id.btnSettings);
        btnInputs.setOnClickListener(v -> host.inputs().showInputs());
        btnWifi.setOnClickListener(v -> openNetworkPill());
        btnVpn.setOnClickListener(v -> openVpnPill());
        btnWifi.setOnLongClickListener(v -> {
            host.open(SystemSettings.intent(host, Settings.ACTION_WIFI_SETTINGS));
            return true;
        });
        launcherSettingsButton = cfg.launcherSettingsButton;
        if (launcherSettingsButton && !cfg.settingsPanel) {
            // With settings_panel the pill keeps its gear and "Settings": the panel it opens holds both kinds.
            ((TextView) btnSettings).setText(R.string.launcher_settings);
            ((TextView) btnSettings).setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_home_settings_pill, 0, 0, 0);
        }
        btnSettings.setOnClickListener(v -> {
            if (launcherSettingsButton) host.startup().openLauncherSettings();
            else host.open(SystemSettings.intent(host, Settings.ACTION_SETTINGS));
        });
        btnReorder = host.findViewById(R.id.btnReorder);
        btnReorder.setOnClickListener(v -> host.toggleReorder());
        btnReorder.setVisibility(cfg.reorderButton ? View.VISIBLE : View.GONE);
        if (!cfg.showNetworkPill) btnWifi.setVisibility(View.GONE);
        showInputsPill = cfg.showInputsPill;
        showInputsMenuRow = cfg.showInputsPill;
        btnLibraryTop = host.findViewById(R.id.btnLibraryTop);
        btnLibraryTop.setOnClickListener(v -> {
            host.grid().openLibraryFromTopBar();   // closing it goes back to this button, not to the pill after the last row
        });
        btnLibraryTop.setVisibility(cfg.libraryButton && cfg.appLibrary ? View.VISIBLE : View.GONE);
        addTopBarApps(cfg.topBarApps, cfg.launchActions);
        iconOnlyButtons = cfg.iconOnlyButtons;
        host.state().topMenu = cfg.topMenu;
        wifiIconOnly = iconOnlyButtons && cfg.wifiLabelWidth <= 0;
        if (cfg.wifiStatusColor) {
            wifiPlainTint = btnWifi.getCompoundDrawableTintList();
            wifiOnlineTint = host.getColorStateList(R.color.pill_icon_online);
            if (cfg.wifiStatusWarnings) {
                wifiNoInternetTint = host.getColorStateList(R.color.pill_icon_no_internet);
                wifiOfflineTint = host.getColorStateList(R.color.pill_icon_offline);
            }
        }
        if (cfg.topMenu) {
            // One gear: Inputs and Wi-Fi move into its dropdown. The Wi-Fi pill stays hidden but keeps its
            // label up to date, which the menu's Wi-Fi row shows.
            showInputsPill = false;
            btnInputs.setVisibility(View.GONE);
            btnWifi.setVisibility(View.GONE);
            ((TextView) btnSettings).setText(R.string.settings);
            ((TextView) btnSettings).setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_settings, 0, 0, 0);
            if (!iconOnlyButtons) makeIconOnly((TextView) btnSettings);
            btnSettings.setOnClickListener(v -> host.popups().showTopMenu());
            // The lone gear sits at the header's middle, not on its bottom line like the row of pills.
            LinearLayout topButtons = host.findViewById(R.id.topButtons);
            RelativeLayout.LayoutParams tlp = (RelativeLayout.LayoutParams) topButtons.getLayoutParams();
            tlp.addRule(RelativeLayout.ALIGN_TOP, R.id.leftBlock);
            tlp.bottomMargin = 0;
            topButtons.setLayoutParams(tlp);
            topButtons.setGravity(Gravity.CENTER_VERTICAL);
        }
        if (iconOnlyButtons) {
            if (cfg.wifiLabelWidth > 0) btnWifi.setMaxWidth(Math.round(cfg.wifiLabelWidth * host.density()));
            else makeIconOnly(btnWifi);
            makeIconOnly((TextView) btnSettings);
        }
        if (cfg.iconOnlyInputs) makeIconOnly((TextView) btnInputs);
        iconOnlyVpn = vpnPill && cfg.iconOnlyVpn;
        if (iconOnlyVpn) {
            vpnPlainBackground = btnVpn.getBackground().getConstantState();
            vpnPlainInk = btnVpn.getCompoundDrawableTintList();
            makeIconOnly(btnVpn);
        }
        btnSettings.setOnLongClickListener(v -> {
            host.popups().showSettingsMenu();
            return true;
        });

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // Back to the dock tile Up came from, not wherever the focus finder lands.
                host.grid().focusTile(host.state().singleRow ? host.grid().lastFocused() : host.grid().lastFocused() % host.state().columns);
                return true;
            }
            int firstButton = btnInputs.getVisibility() == View.VISIBLE ? R.id.btnInputs : firstTopButton().getId();
            // The bar's visual ends: the framework mirrors child order for a right-to-left locale.
            boolean rtl = host.isRtl();
            int leftButton = rtl ? R.id.btnSettings : firstButton;
            int rightButton = rtl ? firstButton : R.id.btnSettings;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return v.getId() == leftButton;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == rightButton;
        };
        btnInputs.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnVpn.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);
        btnReorder.setOnKeyListener(pillKeys);
        btnLibraryTop.setOnKeyListener(pillKeys);
        for (View button : topAppButtons) button.setOnKeyListener(pillKeys);
        if (host.state().advancedEffects) {
            List<View> pills = new ArrayList<>(Arrays.asList(btnInputs, btnWifi, btnVpn, btnReorder, btnLibraryTop, btnSettings));
            pills.addAll(topAppButtons);
            for (View button : pills) {
                button.setOnFocusChangeListener((view, focused) -> view.animate()
                        .scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f)
                        .setDuration(host.state().reducedMotion ? Motion.REDUCED_MS : 160).start());
            }
        }
        // After the scale animation above, so this listener chains it instead of being replaced by it.
        pillLabelsOnFocus = cfg.pillLabelsOnFocus;
        if (cfg.pillLabelsOnFocus) {
            // Each icon-only pill grows to its name while focused and shrinks back when focus leaves. The Library
            // button is icon-only from its layout, so it joins in too.
            if (cfg.iconOnlyInputs) labelOnFocus((TextView) btnInputs);
            if (iconOnlyButtons) {
                if (cfg.wifiLabelWidth <= 0) labelOnFocus(btnWifi);
                labelOnFocus((TextView) btnSettings);
            }
            labelOnFocus((TextView) btnLibraryTop);
        }
    }

    /** onCreate, last: the network, VPN and TV-input callbacks, for the activity's whole life. */
    public void registerCallbacks() {
        ConnectivityManager cm = host.getSystemService(ConnectivityManager.class);
        TvInputManager tim = (TvInputManager) host.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) tim.registerCallback(inputCallback, host.handler());
        if (cm != null) {
            cm.registerDefaultNetworkCallback(networkCallback);
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(), vpnCallback);
        }
    }

    public void unregisterCallbacks() {
        ConnectivityManager cm = host.getSystemService(ConnectivityManager.class);
        TvInputManager tim = (TvInputManager) host.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) tim.unregisterCallback(inputCallback);
        if (cm != null) {
            cm.unregisterNetworkCallback(networkCallback);
            cm.unregisterNetworkCallback(vpnCallback);
        }
    }

    /** onStart: the clock and the time receiver run while Home is visible, paused or not. */
    public void onStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            host.registerReceiver(timeReceiver, timeFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            host.registerReceiver(timeReceiver, timeFilter);
        }
        updateClockVisibility();
        clockRefresh.start();   // with timeReceiver: while Home is visible, paused or not
    }

    public void onStop() {
        host.unregisterReceiver(timeReceiver);
        clockRefresh.stop();
        host.handler().removeCallbacks(clockCheck);
    }

    /**
     * onResume: the greeting, then the pills. The network and Inputs pills keep their state while Home is in the
     * background, and callbacks update them when the network or the inputs change, so this refresh only catches
     * anything missed. Its binder calls (about 7 ms on a TCL C755, most of it WifiManager.getConnectionInfo) would
     * hold up the first frame back on Home, so after the first resume it runs once that frame is drawn.
     */
    public void onResume() {
        updateGreeting();
        if (pillsShown) {
            refreshPillsAfterNextDraw();
        } else {
            updateWifiLabel();
            updateInputsButton();
            pillsShown = true;
        }
        host.handler().postDelayed(minuteTick, MINUTE_MS);
    }

    public void onPause() {
        host.handler().removeCallbacks(minuteTick);
    }

    /** show_inputs_pill as configured: top_menu clears the pill, so the gear menu's Inputs row reads this. */
    public boolean showInputsMenuRow() {
        return showInputsMenuRow;
    }

    /** launcher_settings_button: the Settings pill opens Launcher settings. */
    public boolean launcherSettingsButton() {
        return launcherSettingsButton;
    }

    /** The network pill's text, or its description when the pill is icon-only (the top_menu's Wi-Fi row). */
    @NonNull
    public CharSequence networkPillLabel() {
        CharSequence network = wifiIconOnly ? btnWifi.getContentDescription() : btnWifi.getText();
        return network == null ? "" : network;
    }

    /** The reorder pill lit while reorder mode is on. */
    public void setReorderActive(boolean on) {
        btnReorder.setActivated(on);
    }

    /** The library pill takes focus back when the library it opened closes. */
    public void focusLibraryPill() {
        btnLibraryTop.requestFocus();
    }

    /** The VPN pill takes focus back when the library it opened closes, while it still shows; false when it hid. */
    public boolean focusVpnPillIfShown() {
        if (btnVpn.getVisibility() != View.VISIBLE) return false;
        btnVpn.requestFocus();
        return true;
    }
}
