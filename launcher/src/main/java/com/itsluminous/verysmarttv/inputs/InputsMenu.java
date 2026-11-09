package com.itsluminous.verysmarttv.inputs;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.content.ActivityNotFoundException;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itsluminous.verysmarttv.R;
import com.itsluminous.verysmarttv.home.HomeActivity;
import com.itsluminous.verysmarttv.home.HomeHost;
import com.itsluminous.verysmarttv.theme.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Inputs menu and how an input opens (DESIGN.md §8, moved from the activity verbatim): HDMI ports with their
 * CEC devices and connection states, AV, the Live TV row and the tuner it should tune, the vendor source panel
 * and Coocaa's sources where TvInputManager lists nothing, and the VIEW_INPUTS intent the system sends while
 * handle_view_inputs is on. It also owns the rows the other glass menus are built from.
 */
public final class InputsMenu {
    /** Not private: the layout listener in openInputsIfAsked reads it, and an accessor is what lint's SyntheticAccessor flags. */
    final HomeHost host;
    private final FrameLayout root;
    private final String liveTvPkg;
    private final String liveTvAction;
    private final String liveTvInputId;
    private final String inputAction;
    private final List<String> inputSources;
    private final String inputUriScheme;
    private final String sourcePanelService;
    private final String sourcePanelAction;
    private final String sourcePanelExtra;
    private final boolean coocaaSources;
    /** config.xml inputs_connected_only: the Inputs menu lists only connected inputs, the rest behind "More inputs". */
    private final boolean inputsConnectedOnly;
    /** config.xml inputs_cec_state_only: only CEC device rows show a connection state, bare HDMI ports none. */
    private final boolean inputsCecStateOnly;

    public InputsMenu(@NonNull HomeHost host, @NonNull FrameLayout root, @NonNull Config cfg) {
        this.host = host;
        this.root = root;
        liveTvPkg = cfg.liveTvPackage;
        liveTvAction = cfg.liveTvAction;
        liveTvInputId = cfg.liveTvInputId;
        inputAction = cfg.inputAction;
        inputSources = cfg.inputSources;
        inputUriScheme = cfg.inputUriScheme;
        sourcePanelService = cfg.sourcePanelService;
        sourcePanelAction = cfg.sourcePanelAction;
        sourcePanelExtra = cfg.sourcePanelExtra;
        coocaaSources = cfg.coocaaSources;
        inputsConnectedOnly = cfg.inputsConnectedOnly;
        inputsCecStateOnly = cfg.inputsCecStateOnly;
        // restore_key_services runs before this in onCreate; the VIEW_INPUTS handler follows the theme every start.
        applyViewInputsHandler(cfg.handleViewInputs);
    }

    /** source_panel_service is set: the TV's own source panel can stand in for the list. */
    public boolean hasSourcePanel() {
        return !sourcePanelService.isEmpty();
    }

    /** coocaa_sources: com.skyworth.tv lists the sources (CoocaaSources). */
    public boolean coocaaSources() {
        return coocaaSources;
    }

    /**
     * handle_view_inputs: switch InputsRequestActivity, the VIEW_INPUTS handler, on or off. It is disabled in the
     * manifest, so a TV with its own input picker never sees it; only the sets that need it turn it on. Written
     * only when the state differs, so an ordinary start does not touch the package manager.
     */
    private void applyViewInputsHandler(boolean on) {
        ComponentName handler = new ComponentName(host.activity(), InputsRequestActivity.class);
        PackageManager pm = host.activity().getPackageManager();
        int want = on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        if (pm.getComponentEnabledSetting(handler) != want) {
            pm.setComponentEnabledSetting(handler, want, PackageManager.DONT_KILL_APP);
        }
    }

    /**
     * VIEW_INPUTS, forwarded by InputsRequestActivity while handle_view_inputs is on: open the Inputs menu once
     * the pill is on screen. The intent is replaced so a later recreate() does not
     * open it again. Nothing to do when the TV has no inputs to list (the pill is hidden then).
     * top_menu hides the pill, so the list hangs under the gear there, as in showInputs().
     */
    public boolean openInputsIfAsked(@Nullable Intent intent) {
        if (intent == null || !HomeActivity.ACTION_VIEW_INPUTS.equals(intent.getAction())) return false;
        host.activity().setIntent(new Intent(Intent.ACTION_MAIN));
        View anchor = host.findViewById(host.state().topMenu ? R.id.btnSettings : R.id.btnInputs);
        if (anchor.getVisibility() != View.VISIBLE) return true;
        if (anchor.isLaidOut()) {
            // Android 9 delivers a new intent to a paused activity with a brief resume and pause around it, and
            // onPause() closes popups, so open the menu once that has run (HiSmartTV A4, Android TV 9).
            anchor.post(() -> {
                if (host.activity().isFinishing() || anchor.getVisibility() != View.VISIBLE) return;
                anchor.requestFocus();
                showInputs();
            });
            return true;
        }
        // Cold start: the popup anchors to the pill (or the gear), so wait for the first layout.
        anchor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                anchor.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                anchor.post(() -> {
                    if (host.activity().isFinishing() || anchor.getVisibility() != View.VISIBLE) return;
                    anchor.requestFocus();
                    showInputs();
                });
            }
        });
        return true;
    }

    /** The Live TV app's launch intent: its leanback entry point, or the configured action if it has none. */
    @Nullable
    public Intent liveTvIntent() {
        if (liveTvPkg.isEmpty()) return null;
        Intent i = host.activity().getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (i != null || liveTvAction.isEmpty()) return i;
        Intent byAction = new Intent(liveTvAction).setPackage(liveTvPkg);
        return host.activity().getPackageManager().resolveActivity(byAction, 0) != null ? byAction : null;
    }

    /** The TV's own source-selection panel, for TVs that have no TvInputManager (TCL Android TV 9): a
     *  service, or an activity on sets whose panel is a dialog (BYINTEK U4's QuickSourceActivity). */
    public void openSourcePanel() {
        Intent i = new Intent(sourcePanelAction);
        ComponentName c = ComponentName.unflattenFromString(sourcePanelService);
        if (c != null) i.setComponent(c);
        int eq = sourcePanelExtra.indexOf('=');
        if (eq > 0) {
            try {
                i.putExtra(sourcePanelExtra.substring(0, eq).trim(),
                        Integer.parseInt(sourcePanelExtra.substring(eq + 1).trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        try {
            if (host.activity().getPackageManager().resolveActivity(i, 0) != null) {
                host.activity().startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
            host.activity().startService(i);
        } catch (Exception e) {
            Toast.makeText(host.activity(), R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    /** `input_sources` in config.xml or a theme: "HW5=23" maps the framework input whose id ends in /HW5 to a vendor
     *  source number; "HW8=-" leaves that input out of the Inputs menu (a port the board reports but the set
     *  does not have). Returns the number, -1 for a left-out input, or null when the input is not listed. */
    private Integer mappedSource(String inputId) {
        return InputSources.mapped(inputSources, inputId);
    }

    /** Switches to a passthrough input (HDMI, AV). An input mapped in `input_sources` is opened by sending
     *  `input_action` with its source number as the int extra "input" (Xiaomi China firmware, whose tvplayer
     *  answers com.xiaomi.mitv.tvplayer.EXTSRC_PLAY and nothing else). Otherwise the standard way is
     *  ACTION_VIEW on its channel URI, which the Live TV app handles; where nothing takes that, the
     *  configured `input_action` is sent with the same URI instead (XGIMI's HDMI player only answers
     *  com.xgimi.action.hdmiPlayer). An `input_action` written as "package/.Activity" starts that activity
     *  itself, ahead of the standard way, for firmware that answers the channel URI with "unsupported"
     *  (Redmi X65, MiTV-NSOP0: com.xiaomi.mitv.tvplayer/.ExternalSourceActivity). With `input_uri_scheme` set (YaOS: live-tv) the same URI goes out
     *  with that scheme and no MIME type. A player guarded by a permission this build does not declare
     *  throws SecurityException, which used to take Home down with it (YaOS before the manifest asked
     *  for ACCESS_TV_SOURCE). */
    public void openInput(@NonNull String inputId) {
        // A CEC device is a child input of its HDMI port; the vendor source number belongs to the port
        // (MiTV-MFFU0). Only that lookup uses the parent: the generic path below still opens the child.
        String sourceId = inputId;
        TvInputManager tim = (TvInputManager) host.activity().getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) {
            try {
                TvInputInfo info = tim.getTvInputInfo(inputId);
                if (info != null && info.getParentId() != null) sourceId = info.getParentId();
            } catch (RuntimeException ignored) {
            }
        }
        Integer source = mappedSource(sourceId);
        if (source != null && source >= 0 && !inputAction.isEmpty()) {
            try {
                host.activity().startActivity(inputActionIntent().putExtra("input", source.intValue())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                // fall through to the standard way
            }
        } else if (source == null && InputSources.isComponent(inputAction)) {
            // An activity named in input_action is the vendor's own source picker (Xiaomi: tvplayer's
            // ExternalSourceActivity). Its firmware answers the standard channel URI with "unsupported", so the
            // row goes straight there; the picker is the overlay the remote's Source button opens.
            try {
                host.activity().startActivity(inputActionIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                // fall through to the standard way
            }
        }
        Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
        Intent view = (inputUriScheme.isEmpty()
                ? new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
                : new Intent(Intent.ACTION_VIEW, uri.buildUpon().scheme(inputUriScheme).build()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Aim it at the TV app first: apps installed later (a streaming app, a file manager) can claim the same
        // channel URI too, and with two or more handlers Android shows a chooser instead of the input (WhaleOS).
        if (!liveTvPkg.isEmpty()) {
            try {
                host.activity().startActivity(new Intent(view).setPackage(liveTvPkg));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        }
        try {
            host.activity().startActivity(view);
            return;
        } catch (ActivityNotFoundException | SecurityException e) {
            if (inputAction.isEmpty()) {
                Toast.makeText(host.activity(), R.string.not_available, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        host.open(inputActionIntent().setDataAndType(uri, "vnd.android.cursor.item/channel"));
    }

    /** `input_action` as an intent: an explicit component when it reads "package/.Activity", else an action. */
    private Intent inputActionIntent() {
        ComponentName component = InputSources.isComponent(inputAction)
                ? ComponentName.unflattenFromString(inputAction) : null;
        return component != null ? new Intent().setComponent(component) : new Intent(inputAction);
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    public void showInputs() {
        // A configured source panel and no Live TV row to offer next to it: the panel is the whole menu.
        // The BYINTEK U4 needs this, as its TvInputManager lists three HDMI ports and an AV input it doesn't have.
        List<CoocaaSources.Source> coocaa = coocaaSources ? CoocaaSources.list(host.activity()) : Collections.emptyList();
        if (coocaa.isEmpty() && !sourcePanelService.isEmpty() && liveTvIntent() == null) {
            openSourcePanel();
            return;
        }
        // top_menu: the pill is gone, so the list hangs under the gear, growing inward from the right.
        // Read the switch, not the pill: show_inputs_pill=false hides the pill too, and there the list
        // keeps its usual place.
        boolean underGear = host.state().topMenu;
        View anchor = host.findViewById(underGear ? R.id.btnSettings : R.id.btnInputs);
        View panel = host.activity().getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        int width = Math.max(anchor.getWidth(), (int) (150 * host.density()));
        final PopupWindow popup = host.popups().glassPopup(panel, width);

        TvInputManager tim = (TvInputManager) host.activity().getSystemService(Context.TV_INPUT_SERVICE);
        // inputs_connected_only: ports without a connected device and the AV jacks fold behind a "More inputs" row.
        boolean connectedOnly = inputsConnectedOnly;
        List<View> folded = new ArrayList<>();
        // An explicit root URI asks the Live TV app to leave any HDMI source selected by its own UI.
        boolean rootChannel = TvContract.Channels.CONTENT_URI.toString().equals(liveTvInputId);
        String tunerId = null;
        if (tim != null) {
            // Which rows, in what order, with what label and state, and which tuner: InputRows decides (DESIGN.md §8 rule 2).
            List<InputRows.Input> inputs = new ArrayList<>();
            for (TvInputInfo info : tim.getTvInputList()) {
                CharSequence l = info.loadLabel(host.activity());
                CharSequence custom = InputNames.tidy(info.loadCustomLabel(host.activity()));
                inputs.add(new InputRows.Input(info.getId(), info.getType(), info.getParentId(), info.isPassthroughInput(),
                        info.isHidden(host.activity()),
                        (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0,
                        l == null ? null : l.toString(), custom == null ? null : custom.toString()));
            }
            InputRows.Model model = InputRows.build(inputs, new InputRows.Options(liveTvInputId, rootChannel,
                    inputsCecStateOnly, connectedOnly, id -> Integer.valueOf(-1).equals(mappedSource(id))), tim::getInputState);
            tunerId = model.tunerId;
            for (InputRows.Row r : model.rows) {
                if (r.separatorBefore) {
                    View line = addSeparator(rows);
                    if (connectedOnly) fold(line, folded);
                }
                final String id = r.inputId;
                View row = addInputRow(rows, popup, r.hdmi ? R.drawable.ic_input : R.drawable.ic_av, r.label, r.state,
                        () -> openInput(id));
                if (r.folded) fold(row, folded);
            }
        }
        if (!folded.isEmpty()) {
            View more = host.activity().getLayoutInflater().inflate(R.layout.item_input, rows, false);
            ((ImageView) more.findViewById(R.id.icon)).setImageResource(R.drawable.ic_input);
            ((TextView) more.findViewById(R.id.title)).setText(R.string.more_inputs);
            more.findViewById(R.id.status).setVisibility(View.GONE);
            more.findViewById(R.id.dot).setVisibility(View.GONE);
            more.setOnClickListener(v -> {
                for (View f : folded) f.setVisibility(View.VISIBLE);
                rows.removeView(more);
                for (View f : folded) {
                    if (f.isFocusable()) { f.requestFocus(); break; }
                }
            });
            rows.addView(more);
        }
        // Coocaa OS registers nothing with TvInputManager; com.skyworth.tv lists its sources instead.
        if (rows.getChildCount() == 0) {
            for (CoocaaSources.Source s : coocaa) {
                int icon = "HDMI".equals(s.name) ? R.drawable.ic_input
                        : "VOD".equals(s.name) ? R.drawable.ic_livetv : R.drawable.ic_av;
                addInputRow(rows, popup, icon, s.label, -1, () -> {
                    if (!CoocaaSources.open(host.activity(), s)) {
                        Toast.makeText(host.activity(), R.string.not_available, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
        // Nothing to list where there is no TvInputManager: the TV's own source panel switches inputs.
        if (rows.getChildCount() == 0 && !sourcePanelService.isEmpty()) {
            addInputRow(rows, popup, R.drawable.ic_input, host.activity().getString(R.string.inputs), -1, this::openSourcePanel);
        }
        Intent liveTv = liveTvIntent();
        if (liveTv != null && tunerId != null) {
            // Tune that input only when the Live TV package itself handles the URI.
            // The unrestricted input player also resolves it (that is how HDMI opens), but on the
            // AIWA AI PONT SA that player stays black, and the Live TV package does not handle the
            // URI at all, which used to toast "Not available on this TV". Keep the leanback activity
            // then: it is the same path as the Live TV tile. Do not drop the package restriction.
            // An explicit tuner uses its channel collection (e.g. DVB-S rather than DVB-T/C).
            // Keep the legacy passthrough route for automatic selection: some OEM players need it.
            // If the configured input is absent, tunerId stays null and the app opens normally.
            boolean configured = !liveTvInputId.isEmpty() && !rootChannel;
            Intent tuned = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(configured ? TvContract.buildChannelsUriForInput(tunerId)
                                    : TvContract.buildChannelUriForPassthroughInput(tunerId),
                            configured ? TvContract.Channels.CONTENT_TYPE : TvContract.Channels.CONTENT_ITEM_TYPE)
                    .setPackage(liveTvPkg);
            ComponentName handler = tuned.resolveActivity(host.activity().getPackageManager());
            if (handler != null) {
                // On some MediaTek sets the tuner passthrough URI leaves the app's HDMI source selected.
                // The stock launcher addresses the same activity with the root channel URI instead (#1941).
                liveTv = rootChannel
                        ? new Intent(Intent.ACTION_VIEW).setData(TvContract.Channels.CONTENT_URI).setComponent(handler)
                        : tuned;
            }
        }
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            final Intent liveTvIntent = liveTv;
            addInputRow(rows, popup, R.drawable.ic_livetv, host.activity().getString(R.string.live_tv), -1, () -> host.open(liveTvIntent));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(host.activity(), R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Fit the widest row: TVs that report a status on every port (Sony) would otherwise squeeze the names out.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max(width, Math.min(panel.getMeasuredWidth(), (int) (360 * host.density()))));

        host.popups().dropIn(popup, panel, anchor, underGear ? Gravity.START : Gravity.END);
        for (int i = 0; i < rows.getChildCount(); i++) {
            View child = rows.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE && child.isFocusable()) { child.requestFocus(); break; }
        }
    }

    private static void fold(View view, List<View> folded) {
        view.setVisibility(View.GONE);
        folded.add(view);
    }

    @NonNull
    public View addSeparator(@NonNull LinearLayout rows) {
        View line = new View(host.activity());
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) host.density());
        int m = (int) (6 * host.density()), h = (int) (12 * host.density());
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
        return line;
    }

    @NonNull
    public View addInputRow(@NonNull LinearLayout rows, @NonNull PopupWindow popup, int iconRes, @NonNull String title, int state, @NonNull Runnable action) {
        View row = host.activity().getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
        return row;
    }
}
