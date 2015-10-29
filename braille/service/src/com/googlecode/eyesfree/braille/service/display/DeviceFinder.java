/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.googlecode.eyesfree.braille.service.display;

import com.googlecode.eyesfree.braille.service.R;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.UUID;

/**
 * Finds supported devices among bonded devices.
 */
public class DeviceFinder {

    private static final String LOG_TAG = DeviceFinder.class.getSimpleName();
    private static final UUID SERIAL_BOARD_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String LAST_CONNECTED_DEVICE_KEY =
            "lastBluetoothDevice";

    private final SharedPreferences mSharedPreferences;

    /**
     * Information about a supported bonded bluetooth device.
     */
    public static class DeviceInfo {
        private final BluetoothDevice mBluetoothDevice;
        private final String mDriverCode;
        private final UUID mSdpUuid;
        private final boolean mConnectSecurely;
        private final Map<String, Integer> mFriendlyKeyNames;

        public DeviceInfo(BluetoothDevice bluetoothDevice,
                String driverCode, UUID sdpUuid,
                boolean connectSecurely,
                Map<String, Integer> friendlyKeyNames) {
            mBluetoothDevice = bluetoothDevice;
            mDriverCode = driverCode;
            mSdpUuid = sdpUuid;
            mConnectSecurely = connectSecurely;
            mFriendlyKeyNames = friendlyKeyNames;
        }

        /**
         * Returns the bluetooth device from the system.
         */
        public BluetoothDevice getBluetoothDevice() {
            return mBluetoothDevice;
        }

        /**
         * Returns the brltty driver code to use for this device.
         */
        public String getDriverCode() {
            return mDriverCode;
        }

        /**
         * Returns the service record uuid to use when connecting to
         * this device.
         */
        public UUID getSdpUuid() {
            return mSdpUuid;
        }

        /**
         * Returns whether to connect securely (preferred)
         * or not.
         * @see BluetoothDevice#createInsecureRfcommSocketToServiceRecord
         * @see BluetoothDevice#createRfcommSocketToServiceRecord
         */
        public boolean getConnectSecurely() {
            return mConnectSecurely;
        }

        /**
         */
        public Map<String, Integer> getFriendlyKeyNames() {
            return mFriendlyKeyNames;
        }
    }

    public DeviceFinder(Context context) {
        mSharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Returns a list of bonded and supported devices in the order they
     * should be tried.
     */
    public List<DeviceInfo> findDevices() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return Collections.emptyList();
        }

        List<DeviceInfo> ret = new ArrayList<DeviceInfo>();
        Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
        for (BluetoothDevice dev : bondedDevices) {
            for (SupportedDevice matcher : SUPPORTED_DEVICES) {
                DeviceInfo matched = matcher.match(dev);
                if (matched != null) {
                    ret.add(matched);
                }
            }
        }
        String lastAddress = mSharedPreferences.getString(
            LAST_CONNECTED_DEVICE_KEY, null);
        if (lastAddress != null) {
            // If the last device that was successfully connected is
            // not already first in the list, put it there.
            // (Hence, the 1 below is intentional).
            for (int i = 1; i < ret.size(); ++i) {
                if (ret.get(i).getBluetoothDevice().getAddress().equals(
                        lastAddress)) {
                    Collections.swap(ret, 0, i);
                }
            }
        }
        return ret;
    }

    public void rememberSuccessfulConnection(DeviceInfo info) {
        BluetoothDevice device = info.getBluetoothDevice();
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putString(LAST_CONNECTED_DEVICE_KEY, device.getAddress());
        editor.apply();
    }

    private static interface SupportedDevice {
        DeviceInfo match(BluetoothDevice bluetoothDevice);
    }

    private static class NameRegexSupportedDevice
            implements SupportedDevice {
        private final String mDriverCode;
        private final boolean mConnectSecurely;
        private final Map<String, Integer> mFriendlyKeyNames;
        private final Pattern[] mNameRegexes;

        public NameRegexSupportedDevice(String driverCode,
                boolean connectSecurely,
                Map<String, Integer> friendlyKeyNames,
                Pattern... nameRegexes) {
            mDriverCode = driverCode;
            mConnectSecurely = connectSecurely;
            mFriendlyKeyNames = friendlyKeyNames;
            mNameRegexes = nameRegexes;
        }

        @Override
        public DeviceInfo match(BluetoothDevice bluetoothDevice) {
            String name = bluetoothDevice.getName();
            for (Pattern nameRegex : mNameRegexes) {
                if (nameRegex.matcher(name).lookingAt()) {
                    return new DeviceInfo(bluetoothDevice, mDriverCode,
                            SERIAL_BOARD_UUID, mConnectSecurely,
                            mFriendlyKeyNames);
                }
            }
            return null;
        }
    }

    private static class KeyNameMapBuilder {
        private final Map<String, Integer> mNameMap =
                new HashMap<String, Integer>();

        /**
         * Adds a mapping from the internal {@code name} to a friendly name
         * with resource id {@code resId}.
         */
        public KeyNameMapBuilder add(String name, int resId) {
            mNameMap.put(name, resId);
            return this;
        }

        public KeyNameMapBuilder dots6() {
            add("Dot1", R.string.key_Dot1);
            add("Dot2", R.string.key_Dot2);
            add("Dot3", R.string.key_Dot3);
            add("Dot4", R.string.key_Dot4);
            add("Dot5", R.string.key_Dot5);
            add("Dot6", R.string.key_Dot6);
            return this;
        }

        public KeyNameMapBuilder dots8() {
            dots6();
            add("Dot7", R.string.key_Dot7);
            add("Dot8", R.string.key_Dot8);
            return this;
        }

        public KeyNameMapBuilder routing() {
            return add("RoutingKey", R.string.key_Routing);
        }

        public KeyNameMapBuilder dualJoysticks() {
            add("LeftJoystickLeft", R.string.key_LeftJoystickLeft);
            add("LeftJoystickRight", R.string.key_LeftJoystickRight);
            add("LeftJoystickUp", R.string.key_LeftJoystickUp);
            add("LeftJoystickDown", R.string.key_LeftJoystickDown);
            add("LeftJoystickPress", R.string.key_LeftJoystickCenter);
            add("RightJoystickLeft", R.string.key_RightJoystickLeft);
            add("RightJoystickRight", R.string.key_RightJoystickRight);
            add("RightJoystickUp", R.string.key_RightJoystickUp);
            add("RightJoystickDown", R.string.key_RightJoystickDown);
            add("RightJoystickPress", R.string.key_RightJoystickCenter);
            return this;
        }

        public Map<String, Integer> build() {
            return Collections.unmodifiableMap(mNameMap);
        }
    }

    private static final List<SupportedDevice> SUPPORTED_DEVICES;
    static {
        // TODO: Follow up on why secure connections can't be established
        // with some devices.
        ArrayList<SupportedDevice> l = new ArrayList<SupportedDevice>();

        // BraillePen
        l.add(new NameRegexSupportedDevice("vo", true,
                new KeyNameMapBuilder()
                        .dots6()
                        .add("Shift", R.string.key_BP_Shift)
                        .add("Space", R.string.key_Space)
                        .add("Control", R.string.key_BP_Control)
                        .add("JoystickLeft", R.string.key_JoystickLeft)
                        .add("JoystickRight", R.string.key_JoystickRight)
                        .add("JoystickUp", R.string.key_JoystickUp)
                        .add("JoystickDown", R.string.key_JoystickDown)
                        .add("JoystickEnter", R.string.key_JoystickCenter)
                        .add("ScrollLeft", R.string.key_BP_ScrollLeft)
                        .add("ScrollRight", R.string.key_BP_ScrollRight)
                        .build(),
                        Pattern.compile("EL12-")));

        // Esys
        l.add(new NameRegexSupportedDevice("eu", true,
                new KeyNameMapBuilder()
                        .dots8()
                        .add("Switch1Left", R.string.key_esys_SwitchLeft)
                        .add("Switch1Right", R.string.key_esys_SwitchRight)
                        .dualJoysticks()
                        .add("Backspace", R.string.key_Backspace)
                        .add("Space", R.string.key_Space)
                        .add("RoutingKey1", R.string.key_Routing)
                        .build(),
                        Pattern.compile("Esys-")));

        // Freedom Scientific Focus blue displays.
        l.add(new NameRegexSupportedDevice("fs", true,
                new KeyNameMapBuilder()
                        .dots8()
                        .add("Space", R.string.key_Space)
                        .add("LeftAdvance", R.string.key_focus_LeftAdvance)
                        .add("RightAdvance", R.string.key_focus_RightAdvance)
                        .add("LeftWheelPress",
                                R.string.key_focus_LeftWheelPress)
                        .add("LeftWheelDown",
                                R.string.key_focus_LeftWheelDown)
                        .add("LeftWheelUp",
                                R.string.key_focus_LeftWheelUp)
                        .add("RightWheelPress",
                                R.string.key_focus_RightWheelPress)
                        .add("RightWheelDown",
                                R.string.key_focus_RightWheelDown)
                        .add("RightWheelUp",
                                R.string.key_focus_RightWheelUp)
                        .routing()
                        .add("LeftShift", R.string.key_focus_LeftShift)
                        .add("RightShift", R.string.key_focus_RightShift)
                        .add("LeftGdf", R.string.key_focus_LeftGdf)
                        .add("RightGdf", R.string.key_focus_RightGdf)
                        .add("LeftRockerUp", R.string.key_focus_LeftRockerUp)
                        .add("LeftRockerDown",
                                R.string.key_focus_LeftRockerDown)
                        .add("RightRockerUp", R.string.key_focus_RightRockerUp)
                        .add("RightRockerDown",
                                R.string.key_focus_RightRockerDown)
                        .build(),
                        Pattern.compile("Focus (40|14) BT")));

        // Brailliant
        // Secure connections currently fail on Android devices for the
        // Brailliant.
        l.add(new NameRegexSupportedDevice("hw", false,
                new KeyNameMapBuilder()
                        .dots8()
                        .routing()
                        .add("Space", R.string.key_Space)
                        .add("Power", R.string.key_brailliant_Power)
                        .add("Display1", R.string.key_brailliant_Display1)
                        .add("Display2", R.string.key_brailliant_Display2)
                        .add("Display3", R.string.key_brailliant_Display3)
                        .add("Display4", R.string.key_brailliant_Display4)
                        .add("Display5", R.string.key_brailliant_Display5)
                        .add("Display6", R.string.key_brailliant_Display6)
                        .add("Thumb1", R.string.key_brailliant_Thumb1)
                        .add("Thumb2", R.string.key_brailliant_Thumb2)
                        .add("Thumb3", R.string.key_brailliant_Thumb3)
                        .add("Thumb4", R.string.key_brailliant_Thumb4)
                        .build(),
                        Pattern.compile("Brailliant BI")));

        // HIMS
        l.add(new NameRegexSupportedDevice("hm", false,
                new KeyNameMapBuilder()
                        .dots8()
                        .routing()
                        .add("Space", R.string.key_Space)
                        .add("F1", R.string.key_F1)
                        .add("F2", R.string.key_F2)
                        .add("F3", R.string.key_F3)
                        .add("F4", R.string.key_F4)
                        .add("Backward", R.string.key_Backward)
                        .add("Forward", R.string.key_Forward)
                        .build(),
                        Pattern.compile("Hansone|HansoneXL|BrailleSense|BrailleEDGE|SmartBeetle")));

        // APH Refreshabraille.
        // Secure connections get prematurely closed 50% of the time
        // by the Refreshabraille.
        l.add(new NameRegexSupportedDevice("bm", false,
                new KeyNameMapBuilder()
                        .dots8()
                        .add("Left", R.string.key_JoystickLeft)
                        .add("Right", R.string.key_JoystickRight)
                        .add("Up", R.string.key_JoystickUp)
                        .add("Down", R.string.key_JoystickDown)
                        .add("Press", R.string.key_JoystickCenter)
                        .routing()
                        .add("Display2", R.string.key_APH_AdvanceLeft)
                        .add("Display5", R.string.key_APH_AdvanceRight)
                        .add("B9", R.string.key_Space)
                        .add("B10", R.string.key_Space)
                        .build(),
                        Pattern.compile("Refreshabraille")));
        // Baum VarioConnect
        l.add(new NameRegexSupportedDevice("bm", false,
                new KeyNameMapBuilder()
                        .dots8()
                        .add("Left", R.string.key_JoystickLeft)
                        .add("Right", R.string.key_JoystickRight)
                        .add("Up", R.string.key_JoystickUp)
                        .add("Down", R.string.key_JoystickDown)
                        .add("Press", R.string.key_JoystickCenter)
                        .routing()
                        .add("Display2", R.string.key_APH_AdvanceLeft)
                        .add("Display5", R.string.key_APH_AdvanceRight)
                        .add("B9", R.string.key_Space)
                        .add("B10", R.string.key_Space)
                        .build(),
                        Pattern.compile("VarioConnect")));

        // Older Brailliant, from Humanware group. Uses Baum
        // protocol. No Braille keyboard on this one. Secure
        // connections currently fail on Android devices with this
        // display.
        l.add(new NameRegexSupportedDevice("bm", false,
                new KeyNameMapBuilder()
                        .add("Display1", R.string.key_hwg_brailliant_Display1)
                        .add("Display2", R.string.key_hwg_brailliant_Display2)
                        .add("Display3", R.string.key_hwg_brailliant_Display3)
                        .add("Display4", R.string.key_hwg_brailliant_Display4)
                        .add("Display5", R.string.key_hwg_brailliant_Display5)
                        .add("Display6", R.string.key_hwg_brailliant_Display6)
                        .routing()
                        .build(),
                        Pattern.compile("HWG Brailliant")));

        // Braillex Trio
        l.add(new NameRegexSupportedDevice("pm", true,
                new KeyNameMapBuilder()
                        .dots8()
                        .add("LeftSpace", R.string.key_Space)
                        .add("RightSpace", R.string.key_Space)
                        .add("Space", R.string.key_Space)
                        .add("LeftThumb", R.string.key_braillex_LeftThumb)
                        .add("RightThumb", R.string.key_braillex_RightThumb)
                        .add("RoutingKey1", R.string.key_Routing)
                        .add("BarLeft1", R.string.key_braillex_BarLeft1)
                        .add("BarLeft2", R.string.key_braillex_BarLeft2)
                        .add("BarRight1", R.string.key_braillex_BarRight1)
                        .add("BarRight2", R.string.key_braillex_BarRight2)
                        .add("BarUp1", R.string.key_braillex_BarUp1)
                        .add("BarUp2", R.string.key_braillex_BarUp2)
                        .add("BarDown1", R.string.key_braillex_BarDown1)
                        .add("BarDown2", R.string.key_braillex_BarDown2)
                        .add("LeftKeyRear", R.string.key_braillex_LeftKeyRear)
                        .add("LeftKeyFront", R.string.key_braillex_LeftKeyFront)
                        .add("RightKeyRear", R.string.key_braillex_RightKeyRear)
                        .add("RightKeyFront",
                                R.string.key_braillex_RightKeyFront)
                        .build(),
                        Pattern.compile("braillex trio")));

        // Alva BC640/BC680
        l.add(new NameRegexSupportedDevice("al", false,
                new KeyNameMapBuilder()
                // No braille dot keys.
                .add("ETouchLeftRear", R.string.key_albc_ETouchLeftRear)
                .add("ETouchRightRear", R.string.key_albc_ETouchRightRear)
                .add("ETouchLeftFront", R.string.key_albc_ETouchLeftFront)
                .add("ETouchRightFront", R.string.key_albc_ETouchRightFront)
                .add("SmartpadF1", R.string.key_albc_SmartpadF1)
                .add("SmartpadF2", R.string.key_albc_SmartpadF2)
                .add("SmartpadF3", R.string.key_albc_SmartpadF3)
                .add("SmartpadF4", R.string.key_albc_SmartpadF4)
                .add("SmartpadUp", R.string.key_albc_SmartpadUp)
                .add("SmartpadDown", R.string.key_albc_SmartpadDown)
                .add("SmartpadLeft", R.string.key_albc_SmartpadLeft)
                .add("SmartpadRight", R.string.key_albc_SmartpadRight)
                .add("SmartpadEnter", R.string.key_albc_SmartpadEnter)
                .add("ThumbLeft", R.string.key_albc_ThumbLeft)
                .add("ThumbRight", R.string.key_albc_ThumbRight)
                .add("ThumbUp", R.string.key_albc_ThumbUp)
                .add("ThumbDown", R.string.key_albc_ThumbDown)
                .add("ThumbHome", R.string.key_albc_ThumbHome)
                .add("RoutingKey1", R.string.key_Routing)
                .build(),
                Pattern.compile("Alva BC", Pattern.CASE_INSENSITIVE)));

        // HandyTech displays
        l.add(new NameRegexSupportedDevice("ht", true,
                new KeyNameMapBuilder()
                    .add("B4", R.string.key_Dot1)
                    .add("B3", R.string.key_Dot2)
                    .add("B2", R.string.key_Dot3)
                    .add("B1", R.string.key_Dot7)
                    .add("B5", R.string.key_Dot4)
                    .add("B6", R.string.key_Dot5)
                    .add("B7", R.string.key_Dot6)
                    .add("B8", R.string.key_Dot8)
                    .routing()
                    .add("LeftRockerTop",
                        R.string.key_handytech_LeftTrippleActionTop)
                    .add("LeftRockerBottom",
                        R.string.key_handytech_LeftTrippleActionBottom)
                    .add("LeftRockerTop+LeftRockerBottom",
                        R.string.key_handytech_LeftTrippleActionMiddle)
                    .add("RightRockerTop",
                        R.string.key_handytech_RightTrippleActionTop)
                    .add("RightRockerBottom",
                        R.string.key_handytech_RightTrippleActionBottom)
                    .add("RightRockerTop+RightRockerBottom",
                        R.string.key_handytech_RightTrippleActionMiddle)
                    .add("SpaceLeft", R.string.key_handytech_LeftSpace)
                    .add("SpaceRight", R.string.key_handytech_RightSpace)
                    .add("Display1", R.string.key_hwg_brailliant_Display1)
                    .add("Display2", R.string.key_hwg_brailliant_Display2)
                    .add("Display3", R.string.key_hwg_brailliant_Display3)
                    .add("Display4", R.string.key_hwg_brailliant_Display4)
                    .add("Display5", R.string.key_hwg_brailliant_Display5)
                    .add("Display6", R.string.key_hwg_brailliant_Display6)
                    .build(),
                    Pattern.compile("(Braille Wave( BRW)?|Braillino( BL2)?|Braille Star 40( BS4)?|Easy Braille( EBR)?|Active Braille( AB4)?|Basic Braille BB[3,4,6]?)\\/[a-zA-Z][0-9]-[0-9]{5}"),
                    Pattern.compile("(BRW|BL2|BS4|EBR|AB4|BB(3|4|6)?)\\/[a-zA-Z][0-9]-[0-9]{5}")));

        // Seika Mini Note Taker. Secure connections fail to connect reliably.
        l.add(new NameRegexSupportedDevice("sk", false,
                new KeyNameMapBuilder()
                .dots8()
                .routing()
                .dualJoysticks()
                .add("Backspace", R.string.key_Backspace)
                .add("Space", R.string.key_Space)
                .add("LeftButton", R.string.key_skntk_PanLeft)
                .add("RightButton", R.string.key_skntk_PanRight)
                .build(),
                Pattern.compile("TSM")));

        // Seika Braille Display. No Braille keys on this display.
        l.add(new NameRegexSupportedDevice("sk", true,
                new KeyNameMapBuilder()
                .add("K1", R.string.key_skbdp_PanLeft)
                .add("K8", R.string.key_skbdp_PanRight)
                .add("K2", R.string.key_skbdp_LeftRockerLeft)
                .add("K3", R.string.key_skbdp_LeftRockerRight)
                .add("K4", R.string.key_skbdp_LeftLongKey)
                .add("K5", R.string.key_skbdp_RightLongKey)
                .add("K6", R.string.key_skbdp_RightRockerLeft)
                .add("K7", R.string.key_skbdp_RightRockerRight)
                .add("RoutingKey2", R.string.key_Routing)
                .routing()
                .build(),
                Pattern.compile("TS5")));

        SUPPORTED_DEVICES = Collections.unmodifiableList(l);
    }
}
