/*
 * Copyright (C) 2012 Google Inc.
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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import com.googlecode.eyesfree.compat.CompatUtils;
import com.googlecode.eyesfree.compat.bluetooth.BluetoothProfileCompat.ServiceListenerCompat;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class BluetoothAdapterCompat {
  private static final Class<?> CLASS_BluetoothAdapter =
      CompatUtils.getClass("android.bluetooth.BluetoothAdapter");
  private static final Method METHOD_getProfileProxy =
      CompatUtils.getMethod(
          CLASS_BluetoothAdapter,
          "getProfileProxy",
          Context.class,
          ServiceListenerCompat.CLASS_ServiceListenerCompat,
          int.class);
  private static final Method METHOD_closeProfileProxy =
      CompatUtils.getMethod(
          CLASS_BluetoothAdapter,
          "closeProfileProxy",
          int.class,
          BluetoothProfileCompat.CLASS_BluetoothProfile);
  private static final Method METHOD_getDefaultAdapter =
      CompatUtils.getMethod(CLASS_BluetoothAdapter, "getDefaultAdapter");
  private static final Method METHOD_getBondedDevices =
      CompatUtils.getMethod(CLASS_BluetoothAdapter, "getBondedDevices");
  private static final Method METHOD_isEnabled =
      CompatUtils.getMethod(CLASS_BluetoothAdapter, "isEnabled");

  private final Object mReceiver;

  /**
   * Get a handle to the default local Bluetooth adapter.
   *
   * <p>Currently Android only supports one Bluetooth adapter, but the API could be extended to
   * support more. This will always return the default adapter.
   *
   * @return the default local adapter, or null if Bluetooth is not supported on this hardware
   *     platform
   */
  public static synchronized BluetoothAdapterCompat getDefaultAdapter() {
    final Object result = CompatUtils.invoke(null, null, METHOD_getDefaultAdapter);

    if (result == null) {
      return null;
    }

    return new BluetoothAdapterCompat(result);
  }

  private BluetoothAdapterCompat(Object receiver) {
    mReceiver = receiver;
  }

  /**
   * Returns the underlying BluetoothAdapter object.
   *
   * @return The underlying BluetoothAdapter object.
   */
  public Object getObject() {
    return mReceiver;
  }

  /**
   * Return the set of {@link BluetoothDevice} objects that are bonded (paired) to the local
   * adapter.
   *
   * <p>If Bluetooth state is not {@link BluetoothAdapter#STATE_ON}, this API will return an empty
   * set. After turning on Bluetooth, wait for {@link BluetoothAdapter#ACTION_STATE_CHANGED} with
   * {@link BluetoothAdapter#STATE_ON} to get the updated value.
   *
   * <p>Requires {@link android.Manifest.permission#BLUETOOTH}.
   *
   * @return unmodifiable set of {@link BluetoothDevice}, or null on error
   */
  public Set<BluetoothDeviceCompat> getBondedDevices() {
    final Set<?> results = (Set<?>) CompatUtils.invoke(mReceiver, null, METHOD_getBondedDevices);

    if (results == null) {
      return Collections.emptySet();
    }

    final Set<BluetoothDeviceCompat> output = new HashSet<BluetoothDeviceCompat>();

    for (Object result : results) {
      output.add(new BluetoothDeviceCompat(result));
    }

    return Collections.unmodifiableSet(output);
  }

  /**
   * Get the profile proxy object associated with the profile.
   *
   * <p>Profile can be one of {@link BluetoothProfileCompat#HEALTH}, {@link
   * BluetoothProfileCompat#HEADSET} or {@link BluetoothProfileCompat#A2DP}. Clients must implements
   * {@link BluetoothProfileCompat.ServiceListenerCompat} to get notified of the connection status
   * and to get the proxy object.
   *
   * @param context Context of the application
   * @param listener The service Listener for connection callbacks.
   * @param profile The Bluetooth profile; either {@link BluetoothProfileCompat#HEALTH}, {@link
   *     BluetoothProfileCompat#HEADSET} or {@link BluetoothProfileCompat#A2DP}.
   * @return true on success, false on error
   */
  public boolean getProfileProxy(Context context, ServiceListenerCompat listener, int profile) {
    return (Boolean)
        CompatUtils.invoke(mReceiver, false, METHOD_getProfileProxy, context, listener);
  }

  /**
   * Close the connection of the profile proxy to the Service.
   *
   * <p>Clients should call this when they are no longer using the proxy obtained from {@link
   * #getProfileProxy}. Profile can be one of {@link BluetoothProfileCompat#HEALTH}, {@link
   * BluetoothProfileCompat#HEADSET} or {@link BluetoothProfileCompat#A2DP}
   *
   * @param profile
   * @param proxy Profile proxy object
   */
  public void closeProfileProxy(int profile, Object proxy) {
    CompatUtils.invoke(mReceiver, null, METHOD_closeProfileProxy, profile, proxy);
  }

  /**
   * Return true if Bluetooth is currently enabled and ready for use.
   *
   * <p>Equivalent to: <code>getBluetoothState() == STATE_ON</code>
   *
   * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
   *
   * @return true if the local adapter is turned on
   */
  public boolean isEnabled() {
    return (Boolean) CompatUtils.invoke(mReceiver, false, METHOD_isEnabled);
  }
}
