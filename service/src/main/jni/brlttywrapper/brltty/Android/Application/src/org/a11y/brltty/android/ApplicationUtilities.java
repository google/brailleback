/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 1995-2018 by The BRLTTY Developers.
 *
 * BRLTTY comes with ABSOLUTELY NO WARRANTY.
 *
 * This is free software, placed under the terms of the
 * GNU Lesser General Public License, as published by the Free Software
 * Foundation; either version 2.1 of the License, or (at your option) any
 * later version. Please see the file LICENSE-LGPL for details.
 *
 * Web Page: http://brltty.com/
 *
 * This software is maintained by Dave Mielke <dave@mielke.cc>.
 */

package org.a11y.brltty.android;


import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.usb.UsbManager;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

public abstract class ApplicationUtilities {
  public static boolean haveSdkVersion(int version) {
    return ApplicationParameters.SDK_VERSION >= version;
  }

  public static String getResourceString(int identifier) {
    return ApplicationHooks.getContext().getString(identifier);
  }

  public static ContentResolver getContentResolver() {
    return ApplicationHooks.getContext().getContentResolver();
  }

  public static String getSecureSetting(String setting) {
    return Settings.Secure.getString(getContentResolver(), setting);
  }

  public static String getSelectedInputMethodIdentifier() {
    return getSecureSetting(Settings.Secure.DEFAULT_INPUT_METHOD);
  }

  public static SharedPreferences getSharedPreferences() {
    return PreferenceManager.getDefaultSharedPreferences(ApplicationHooks.getContext());
  }

  private static final SingletonReference<PowerManager> powerManagerReference =
      new SystemServiceReference<PowerManager>(Context.POWER_SERVICE);

  public static PowerManager getPowerManager() {
    return powerManagerReference.get();
  }

  private static final SingletonReference<KeyguardManager> keyguardManagerReference =
      new SystemServiceReference<KeyguardManager>(Context.KEYGUARD_SERVICE);

  public static KeyguardManager getKeyguardManager() {
    return keyguardManagerReference.get();
  }

  private static final SingletonReference<InputMethodManager> inputMethodManagerReference =
      new SystemServiceReference<InputMethodManager>(Context.INPUT_METHOD_SERVICE);

  public static InputMethodManager getInputMethodManager() {
    return inputMethodManagerReference.get();
  }

  private static final SingletonReference<WindowManager> windowManagerReference =
      new SystemServiceReference<WindowManager>(Context.WINDOW_SERVICE);

  public static WindowManager getWindowManager() {
    return windowManagerReference.get();
  }

  private static final SingletonReference<UsbManager> usbManagerReference =
      new SystemServiceReference<UsbManager>(Context.USB_SERVICE);

  public static UsbManager getUsbManager() {
    return usbManagerReference.get();
  }

  private ApplicationUtilities() {}
}
