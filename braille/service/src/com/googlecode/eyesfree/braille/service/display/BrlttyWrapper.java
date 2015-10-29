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

import com.googlecode.eyesfree.braille.display.BrailleDisplayProperties;
import com.googlecode.eyesfree.braille.display.BrailleKeyBinding;

import android.content.res.Resources;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper for the driver functionality of brltty.
 *
 * This class prvoides a low-level interface to the functionality
 * of brltty that is used to control braille displays.
 *
 * Since brltty is single-threaded and relies on some global state,
 * there can only be one instance of this class active at any given time.
 * In addition, after construction, all method calls must be made from
 * one thread (which may be different from the thread used to
 * construct the object).  The one exception to this rules is
 * {@link #addBytesFromDevice()}, which can be called from
 * any thread.
 */
public class BrlttyWrapper {

    private static final String LOG_TAG = BrlttyWrapper.class.getSimpleName();

    private final DriverThread mDriverThread;
    private final Resources mResources;
    private final String mTablesDir;
    private final DeviceFinder.DeviceInfo mDeviceInfo;
    /** Native pointer to C struct */
    @SuppressWarnings("unused")
    private int mNativeData;

    /**
     * Constructs a {@link BrlttyWrapper}.  {@code driverThread} is used
     * to send data bytes to the device.
     */
    public BrlttyWrapper(DeviceFinder.DeviceInfo deviceInfo,
            DriverThread driverThread,
            Resources resources,
            File tablesDir) {
        mDeviceInfo = deviceInfo;
        mDriverThread = driverThread;
        mResources = resources;
        mTablesDir = tablesDir.getPath();
        initNative();
    }

    /**
     * Initializes the driver, making sure there is a device
     * connected.
     */
    public boolean start() {
        return startNative(mDeviceInfo.getDriverCode(), deviceBrlttyAddress());
    }

    /**
     * Uninitializes the driver and cleans up any resources allocated
     * by this object.
     */
    public void stop() {
        stopNative();
    }

    public BrailleDisplayProperties getDisplayProperties() {
        BrailleKeyBinding[] keyBindings = getKeyMap();
        return new BrailleDisplayProperties(
            getTextCellsNative(), getStatusCellsNative(),
            keyBindings, getFriendlyKeyNames(keyBindings));
    }

    /**
     * Sets the content of the braille display to {@code pattern}, where
     * each byte represents one braille cell.
     * {@code pattern} should be as large as the total number of cells
     * on the display.
     */
    public boolean writeWindow(byte[] pattern) {
        return writeWindowNative(pattern);
    }

    /**
     * Polls the driver for a single key command, returning -1 if no command
     * is available.
     *
     * This is typically used when more input data from the device
     * has been detected.  This call does not block indefinitely, but it can
     * still block for small intervals while waiting for more input
     * from the device.
     */
    public int readCommand() {
        int res = readCommandNative();
        return res;
    }

    /**
     * Adds more data to be consumed by the driver.  This method can be
     * called at any time after an object of this class has been constructed
     * until {@link #stop()} is called.  It can be called from
     * any thread.  In particular, it is important to keep calling
     * this method during other calls that might consume input so that they
     * can proceed and consume that input.
     */
    public void addBytesFromDevice(byte[] bytes, int size) throws IOException {
        addBytesFromDeviceNative(bytes, size);
    }

    /**
     * Returns the address of the connected device in the form expected
     * by the brltty drivers.
     */
    private String deviceBrlttyAddress() {
        return String.format("bluetooth:%s",
                mDeviceInfo.getBluetoothDevice().getAddress());
    }

    private BrailleKeyBinding[] getKeyMap() {
        BrailleKeyBinding[] fullKeyMap = getKeyMapNative();
        ArrayList<BrailleKeyBinding> arrayList =
                new ArrayList<BrailleKeyBinding>(fullKeyMap.length);
        for (int i = 0, len = fullKeyMap.length; i < len; ++i) {
            BrailleKeyBinding binding = fullKeyMap[i];
            if (hasAllFriendlyKeyNames(binding)) {
                arrayList.add(binding);
            }
        }
        if (arrayList.size() == fullKeyMap.length) {
            return fullKeyMap;
        }
        BrailleKeyBinding[] relevantBindings = arrayList.toArray(
                fullKeyMap);
        return Arrays.copyOf(relevantBindings, arrayList.size());
    }

    private boolean hasAllFriendlyKeyNames(BrailleKeyBinding binding) {
        Map<String, Integer> friendlyNames = mDeviceInfo.getFriendlyKeyNames();
        for (String key : binding.getKeyNames()) {
            if (!friendlyNames.containsKey(key)) {
                return false;
            }
        }
        return true;
    }

    private Map<String, String> getFriendlyKeyNames(
            BrailleKeyBinding[] bindings) {
        Map<String, String> result = new HashMap<String, String>();
        Map<String, Integer> friendlyNames = mDeviceInfo.getFriendlyKeyNames();
        for (BrailleKeyBinding binding : bindings) {
            for (String key : binding.getKeyNames()) {
                Integer resId = friendlyNames.get(key);
                if (resId != null) {
                    result.put(key, mResources.getString(resId));
                } else {
                    result.put(key, key);
                }
            }
        }
        return result;
    }

    // Native methods.

    private native boolean initNative();
    private native boolean startNative(String driverCode,
            String brailleDevice);
    private native void stopNative();
    private native boolean writeWindowNative(byte[] pattern);
    private native int readCommandNative();
    private native void addBytesFromDeviceNative(byte[] bytes, int size)
        throws IOException;
    private native BrailleKeyBinding[] getKeyMapNative();
    private native int getTextCellsNative();
    private native int getStatusCellsNative();

    // Callbacks used from native code.

    /**
     * Writes bytes from the driver over bluetooth to the device.
     */
    @SuppressWarnings("unused")
    private boolean sendBytesToDevice(byte[] command) {
        return mDriverThread.sendBytesToDevice(command);
    }

    /**
     * Called from the command logic in the driver to have the driver thread
     * woken up to read another command after {@code delayMillis}
     * milliseconds.
     */
    @SuppressWarnings("unused")
    private void readDelayed(long delayMillis) {
        mDriverThread.readDelayed(delayMillis);
    }

    // End callbacks.

    private static native void classInitNative();
    static {
        System.loadLibrary("brlttywrap");
        classInitNative();
    }
}
