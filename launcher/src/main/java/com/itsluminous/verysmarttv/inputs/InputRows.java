package com.itsluminous.verysmarttv.inputs;

import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Which rows the Inputs menu lists, in what order, with what label and state, and which tuner the Live TV row
 * should tune (DESIGN.md §8 rule 2: the decisions of {@code InputsMenu.showInputs}, pure). The menu turns the
 * TV's {@link TvInputInfo}s into {@link Input}s, asks {@link #build} for the rows, and only draws them.
 */
public final class InputRows {
    private InputRows() {
    }

    /** A framework input as the menu needs it; the menu builds one per {@link TvInputInfo}. */
    public static final class Input {
        public final String id;
        /** {@link TvInputInfo#getType()}: TYPE_HDMI, TYPE_TUNER, TYPE_COMPOSITE, ... */
        public final int type;
        /** The port a CEC device sits on ({@link TvInputInfo#getParentId()}), or null. */
        @Nullable
        public final String parentId;
        public final boolean passthrough;
        /** Hidden in the TV's Settings > Inputs. */
        public final boolean hidden;
        /** A tuner registered by a system app (not AirPlay's software tuner, say). */
        public final boolean systemApp;
        /** {@link TvInputInfo#loadLabel}, or null. */
        @Nullable
        public final String label;
        /** {@link TvInputInfo#loadCustomLabel} after {@link InputNames#tidy}, or null. */
        @Nullable
        public final String customLabel;

        public Input(@NonNull String id, int type, @Nullable String parentId, boolean passthrough, boolean hidden,
                     boolean systemApp, @Nullable String label, @Nullable String customLabel) {
            this.id = id;
            this.type = type;
            this.parentId = parentId;
            this.passthrough = passthrough;
            this.hidden = hidden;
            this.systemApp = systemApp;
            this.label = label;
            this.customLabel = customLabel;
        }
    }

    /** What the menu reads from the theme and config for the list. */
    public static final class Options {
        /** live_tv_input_id: the tuner to prefer, the root channel URI, or empty for any system tuner. */
        public final String liveTvInputId;
        /** live_tv_input_id is the root channel URI: any system tuner qualifies, and Live TV keeps its own source. */
        public final boolean rootChannel;
        /** inputs_cec_state_only: ports show no state, CEC device rows show their own. */
        public final boolean cecStateOnly;
        /** inputs_connected_only: ports without a connected device and the AV jacks fold behind "More inputs". */
        public final boolean connectedOnly;
        /** input_sources: true for a port the list leaves out ("HW8=-"). */
        public final Function<String, Boolean> leftOut;

        public Options(@NonNull String liveTvInputId, boolean rootChannel, boolean cecStateOnly, boolean connectedOnly,
                       @NonNull Function<String, Boolean> leftOut) {
            this.liveTvInputId = liveTvInputId;
            this.rootChannel = rootChannel;
            this.cecStateOnly = cecStateOnly;
            this.connectedOnly = connectedOnly;
            this.leftOut = leftOut;
        }
    }

    /** One row of the menu. */
    public static final class Row {
        /** An HDMI port or the CEC device on it (the HDMI icon); false for AV and the rest (the AV icon). */
        public final boolean hdmi;
        public final String label;
        /** {@link TvInputManager#INPUT_STATE_CONNECTED}, INPUT_STATE_DISCONNECTED, or -1 for no state shown. */
        public final int state;
        /** The input the row opens. */
        public final String inputId;
        /** inputs_connected_only: the row starts hidden behind "More inputs". */
        public final boolean folded;
        /** A separator line goes above this row: the first non-HDMI row after HDMI rows (folded with the row). */
        public final boolean separatorBefore;

        Row(boolean hdmi, @NonNull String label, int state, @NonNull String inputId, boolean folded, boolean separatorBefore) {
            this.hdmi = hdmi;
            this.label = label;
            this.state = state;
            this.inputId = inputId;
            this.folded = folded;
            this.separatorBefore = separatorBefore;
        }
    }

    /** The rows in menu order, and the tuner the Live TV row tunes (null for none). */
    public static final class Model {
        public final List<Row> rows;
        @Nullable
        public final String tunerId;

        Model(@NonNull List<Row> rows, @Nullable String tunerId) {
            this.rows = rows;
            this.tunerId = tunerId;
        }
    }

    /**
     * The menu for these inputs. {@code stateOf} answers {@link TvInputManager#getInputState} for an id; it is only
     * asked for the ports and devices whose state the menu shows, as the menu itself did.
     */
    @NonNull
    public static Model build(@NonNull List<Input> listed, @NonNull Options o, @NonNull Function<String, Integer> stateOf) {
        List<Input> inputs = new ArrayList<>(listed);
        // HDMI ports first in port order, then AV and the rest.
        Collections.sort(inputs, (a, b) -> {
            boolean ha = a.type == TvInputInfo.TYPE_HDMI, hb = b.type == TvInputInfo.TYPE_HDMI;
            if (ha != hb) return ha ? -1 : 1;
            return String.valueOf(a.label).compareToIgnoreCase(String.valueOf(b.label));
        });
        // An HDMI-CEC device (an Apple TV, a soundbar) gets an input of its own whose parent is the
        // port it sits on, so the port and the device would each get a row (Vestel MT7332: "HDMI 1"
        // and "HDMI Apple TV"). The device takes its port's row instead, and a bare port is listed
        // only when nothing on it speaks CEC.
        Set<String> inputIds = new HashSet<>();
        for (Input info : inputs) inputIds.add(info.id);
        Map<String, List<Input>> cecDevices = new HashMap<>();
        for (Input info : inputs) {
            String parent = info.parentId;
            if (parent == null || !inputIds.contains(parent)) continue;
            if (!info.passthrough || info.hidden) continue;
            List<Input> onPort = cecDevices.get(parent);
            if (onPort == null) cecDevices.put(parent, onPort = new ArrayList<>());
            onPort.add(info);
        }
        // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
        // so drop the HDMI status then. A TV with every port really in use loses it too.
        int hdmiPorts = 0, hdmiConnected = 0;
        for (Input info : inputs) {
            if (!info.passthrough || info.type != TvInputInfo.TYPE_HDMI) continue;
            if (info.parentId != null && inputIds.contains(info.parentId)) continue;
            hdmiPorts++;
            if (stateOf.apply(info.id) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
        }
        // inputs_cec_state_only: the TV puts port states on the wrong port (TCL UnionTV, Android 9), so
        // ports show none, and a CEC device row shows its own state, which the CEC bus keeps right.
        boolean hdmiStateBogus = o.cecStateOnly || (hdmiPorts > 1 && hdmiConnected == hdmiPorts);
        List<Row> rows = new ArrayList<>();
        boolean sawNonHdmi = false;
        String tunerId = null;
        boolean tunerIsHw = false;
        boolean tunerIsAnalog = false;
        for (Input info : inputs) {
            if (info.type == TvInputInfo.TYPE_TUNER && info.systemApp
                    && (o.liveTvInputId.isEmpty() || o.rootChannel || o.liveTvInputId.equals(info.id))) {
                // Prefer a hardware tuner: the framework names those ".../HW<n>". System apps such as
                // AirPlay also register software inputs of type TUNER and may sort first (seen on TCL G05).
                // Among tuners of the same kind, skip an analog input when another is present.
                // MediaTek names them AnalogInputService and TunerInputService; alphabetical order
                // would pick the analog one, and Live TV then opens a dead ATV source.
                boolean hw = info.id.matches(".*/HW\\d+$");
                boolean analog = info.id.toLowerCase(Locale.ROOT).contains("analog");
                boolean better = tunerId == null
                        || (hw && !tunerIsHw)
                        || (hw == tunerIsHw && tunerIsAnalog && !analog)
                        || (hw == tunerIsHw && analog == tunerIsAnalog
                                && info.id.compareTo(tunerId) < 0);
                if (better) {
                    tunerId = info.id;
                    tunerIsHw = hw;
                    tunerIsAnalog = analog;
                }
            }
            // Inputs hidden in the TV's Settings > Inputs stay out of the menu too.
            if (!info.passthrough || info.hidden) continue;
            // input_sources can leave out ports the board reports but the set does not have ("HW8=-").
            if (Boolean.TRUE.equals(o.leftOut.apply(info.id))) continue;
            // A CEC device's row is added with its port, below.
            if (info.parentId != null && inputIds.contains(info.parentId)) continue;
            String label = info.label == null ? info.id : info.label;
            boolean hdmi = info.type == TvInputInfo.TYPE_HDMI;
            if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
            String custom = info.customLabel;
            boolean named = custom != null && custom.length() > 0 && !custom.equals(label);
            boolean separator = !hdmi && !rows.isEmpty() && !sawNonHdmi;
            if (!hdmi) sawNonHdmi = true;
            List<Input> devices = cecDevices.get(info.id);
            if (devices != null) {
                // "Apple TV · HDMI 1": the device's own name (or the one given to it or to its port in
                // Settings > Inputs), then the port. Opening the device's input also makes it the CEC
                // active source, which wakes it.
                for (Input device : devices) {
                    String name = device.customLabel;
                    if (name == null || name.length() == 0) name = named ? custom : device.label;
                    String deviceLabel = name == null || name.length() == 0 ? label : name + " \u00B7 " + label;
                    int state = hdmiStateBogus && !o.cecStateOnly ? -1 : stateOf.apply(device.id);
                    rows.add(new Row(true, deviceLabel, state, device.id, o.connectedOnly && InputFold.folds(true, state), separator));
                    separator = false;
                }
                continue;
            }
            // The name given in Settings > Inputs (e.g. "MacBook Pro") goes first, the port after it.
            if (named) label = custom + " \u00B7 " + label;
            // Analog AV jacks have no hotplug detection, and some TVs report them as always connected
            // (TCL Smart TV Pro G03 says "Connected" with nothing plugged in), so AV rows show no state.
            int state = !hdmi || hdmiStateBogus ? -1 : stateOf.apply(info.id);
            rows.add(new Row(hdmi, label, state, info.id, o.connectedOnly && InputFold.folds(hdmi, state), separator));
        }
        return new Model(rows, tunerId);
    }
}
