/*
 * Copyright (C) 2011 Google Inc.
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

package com.googlecode.eyesfree.compat.bluetooth;

import com.googlecode.eyesfree.compat.CompatUtils;

import java.lang.reflect.Method;

public class BluetoothHeadsetCompat {
    private static final Class<?> CLASS_BluetoothDevice = CompatUtils
            .getClass("android.bluetooth.BluetoothDevice");
    private static final Class<?> CLASS_BluetoothHeadset = CompatUtils
            .getClass("android.bluetooth.BluetoothHeadset");
    private static final Method METHOD_startScoUsingVirtualVoiceCall = CompatUtils.getMethod(
            CLASS_BluetoothHeadset, "startScoUsingVirtualVoiceCall", CLASS_BluetoothDevice);
    private static final Method METHOD_stopScoUsingVirtualVoiceCall = CompatUtils.getMethod(
            CLASS_BluetoothHeadset, "stopScoUsingVirtualVoiceCall", CLASS_BluetoothDevice);
    private static final Method METHOD_isAudioConnected = CompatUtils.getMethod(
            CLASS_BluetoothHeadset, "isAudioConnected", CLASS_BluetoothDevice);

    private final Object mReceiver;

    public BluetoothHeadsetCompat(Object receiver) {
        mReceiver = receiver;
    }

    /**
     * Check if Bluetooth SCO audio is connected.
     * <p>
     * Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param device Bluetooth headset
     * @return true if SCO is connected, false otherwise or on error
     */
    public boolean isAudioConnected(BluetoothDeviceCompat device) {
        return (Boolean) CompatUtils.invoke(mReceiver, false, METHOD_isAudioConnected, device.getObject());
    }

    /**
     * Initiates a SCO channel connection with the headset (if connected). Also
     * initiates a virtual voice call for Handsfree devices as many devices do
     * not accept SCO audio without a call. This API allows the handsfree device
     * to be used for routing non-cellular call audio.
     *
     * @param device Remote Bluetooth Device
     * @return true if successful, false if there was some error.
     */
    public boolean startScoUsingVirtualVoiceCall(
            BluetoothDeviceCompat device) {
        return (Boolean) CompatUtils.invoke(mReceiver, false, METHOD_startScoUsingVirtualVoiceCall,
                device.getObject());
    }

    /**
     * Terminates an ongoing SCO connection and the associated virtual call.
     *
     * @param device Remote Bluetooth Device
     * @return true if successful, false if there was some error.
     */
    public boolean stopScoUsingVirtualVoiceCall(
            BluetoothDeviceCompat device) {
        return (Boolean) CompatUtils.invoke(mReceiver, false, METHOD_stopScoUsingVirtualVoiceCall,
                device.getObject());
    }
}
