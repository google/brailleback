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

import android.os.Build;
import com.googlecode.eyesfree.compat.CompatUtils;
import com.googlecode.eyesfree.compat.bluetooth.BluetoothProfileCompatHoneycomb.ServiceListenerBridge;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BluetoothProfileCompat {
  interface BluetoothProfileVersionImpl {
    public Object newServiceListener(final ServiceListenerCompat listener);
  }

  static class BluetoothProfileStubImpl implements BluetoothProfileVersionImpl {
    @Override
    public Object newServiceListener(final ServiceListenerCompat listener) {
      return null;
    }
  }

  static class BluetoothProfileHoneycombImpl extends BluetoothProfileStubImpl {
    @Override
    public Object newServiceListener(final ServiceListenerCompat listener) {
      return BluetoothProfileCompatHoneycomb.newServiceListener(
          new ServiceListenerBridge() {
            @Override
            public void onServiceConnected(int profile, Object proxy) {
              listener.onServiceConnected(profile, proxy);
            }

            @Override
            public void onServiceDisconnected(int profile) {
              listener.onServiceDisconnected(profile);
            }
          });
    }
  }

  static {
    if (Build.VERSION.SDK_INT >= 11) { // ICS
      IMPL = new BluetoothProfileHoneycombImpl();
    } else {
      IMPL = new BluetoothProfileStubImpl();
    }
  }

  private static final BluetoothProfileVersionImpl IMPL;

  /* package */ static final Class<?> CLASS_BluetoothProfile =
      CompatUtils.getClass("android.bluetooth.BluetoothProfile");

  private static final Method METHOD_getConnectedDevices =
      CompatUtils.getMethod(CLASS_BluetoothProfile, "getConnectedDevices");

  /**
   * Extra for the connection state intents of the individual profiles. This extra represents the
   * current connection state of the profile of the Bluetooth device.
   */
  public static final String EXTRA_STATE = "android.bluetooth.profile.extra.STATE";

  /**
   * Extra for the connection state intents of the individual profiles. This extra represents the
   * previous connection state of the profile of the Bluetooth device.
   */
  public static final String EXTRA_PREVIOUS_STATE =
      "android.bluetooth.profile.extra.PREVIOUS_STATE";

  /** The profile is in disconnected state */
  public static final int STATE_DISCONNECTED = 0;
  /** The profile is in connecting state */
  public static final int STATE_CONNECTING = 1;
  /** The profile is in connected state */
  public static final int STATE_CONNECTED = 2;
  /** The profile is in disconnecting state */
  public static final int STATE_DISCONNECTING = 3;

  /** Headset and Handsfree profile */
  public static final int HEADSET = 1;
  /** A2DP profile. */
  public static final int A2DP = 2;
  /** Health Profile */
  public static final int HEALTH = 3;

  /**
   * Get connected devices for this specific profile.
   *
   * <p>Return the set of devices which are in state {@link #STATE_CONNECTED}
   *
   * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
   *
   * @param receiver The receiving BluetoothProfile implementation.
   * @return List of devices. The list will be empty on error.
   */
  public static List<BluetoothDeviceCompat> getConnectedDevices(Object receiver) {
    final List<?> results =
        (List<?>) CompatUtils.invoke(receiver, null, METHOD_getConnectedDevices);

    if (results == null) {
      return Collections.emptyList();
    }

    final List<BluetoothDeviceCompat> output = new ArrayList<BluetoothDeviceCompat>(results.size());

    for (Object result : results) {
      output.add(new BluetoothDeviceCompat(result));
    }

    return output;
  }

  public abstract static class ServiceListenerCompat {
    static final Class<?> CLASS_ServiceListenerCompat =
        CompatUtils.getClass("android.bluetooth.BluetoothProfile.ServiceListener");

    final Object mListener;

    public ServiceListenerCompat() {
      mListener = IMPL.newServiceListener(this);
    }

    /**
     * Called to notify the client when the proxy object has been connected to the service.
     *
     * @param profile - One of {@link #HEALTH}, {@link #HEADSET} or {@link #A2DP}
     * @param proxy - One of {@link BluetoothHeadsetCompat}
     */
    public abstract void onServiceConnected(int profile, Object proxy);

    /**
     * Called to notify the client that this proxy object has been disconnected from the service.
     *
     * @param profile - One of {@link #HEALTH}, {@link #HEADSET} or {@link #A2DP}
     */
    public abstract void onServiceDisconnected(int profile);
  }
}
