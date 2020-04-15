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

package com.googlecode.eyesfree.utils;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;

/** Utility methods for interacting with {@link SharedPreferences} objects. */
public class SharedPreferencesUtils {
  /**
   * Returns the value of an integer preference stored as a string. This is necessary when using a
   * {@link android.preference.ListPreference} to manage an integer preference, since the entries
   * must be {@link String} values.
   *
   * @param prefs Shared preferences from which to obtain the value.
   * @param res Resources from which to obtain the key and default value.
   * @param keyResId Resource identifier for the key.
   * @param defaultResId Resource identifier for the default value.
   * @return The preference value, or the default value if not set.
   */
  public static int getIntFromStringPref(
      SharedPreferences prefs, Resources res, int keyResId, int defaultResId) {
    final String strPref = prefs.getString(res.getString(keyResId), res.getString(defaultResId));
    return Integer.parseInt(strPref);
  }

  /**
   * Returns the value of a floating point preference stored as a string. This is necessary when
   * using a {@link android.preference.ListPreference} to manage a floating point preference, since
   * the entries must be {@link String} values.
   *
   * @param prefs Shared preferences from which to obtain the value.
   * @param res Resources from which to obtain the key and default value.
   * @param keyResId Resource identifier for the key.
   * @param defaultResId Resource identifier for the default value.
   * @return The preference value, or the default value if not set.
   */
  public static float getFloatFromStringPref(
      SharedPreferences prefs, Resources res, int keyResId, int defaultResId) {
    final String strPref = prefs.getString(res.getString(keyResId), res.getString(defaultResId));
    return Float.parseFloat(strPref);
  }

  /**
   * Returns the value of a string preference.
   *
   * @param prefs Shared preferences from which to obtain the value.
   * @param res Resources from which to obtain the key and default value.
   * @param keyResId Resource identifier for the key.
   * @param defaultResId Resource identifier for the default value.
   * @return The preference value, or the default value if not set.
   */
  public static String getStringPref(
      SharedPreferences prefs, Resources res, int keyResId, int defaultResId) {
    return prefs.getString(
        res.getString(keyResId), ((defaultResId == 0) ? null : res.getString(defaultResId)));
  }

  /**
   * Returns the value of an integer preference.
   *
   * @param prefs Shared preferences from which to obtain the value.
   * @param res Resources from which to obtain the key and default value.
   * @param keyResId Resource identifier for the key.
   * @param defaultResId Resource identifier for the default value.
   * @return The preference value, or the default value if not set.
   */
  public static int getIntPref(
      SharedPreferences prefs, Resources res, int keyResId, int defaultResId) {
    if (defaultResId == 0) {
      throw new IllegalArgumentException("defaultResId should not be 0");
    }
    return prefs.getInt(res.getString(keyResId), res.getInteger(defaultResId));
  }

  /**
   * Returns the value of a boolean preference.
   *
   * @param prefs Shared preferences from which to obtain the value.
   * @param res Resources from which to obtain the key and default value.
   * @param keyResId Resource identifier for the key.
   * @param defaultResId Resource identifier for the default value.
   * @return The preference value, or the default value if not set.
   */
  public static boolean getBooleanPref(
      SharedPreferences prefs, Resources res, int keyResId, int defaultResId) {
    return prefs.getBoolean(res.getString(keyResId), res.getBoolean(defaultResId));
  }

  /**
   * Stores the value of a boolean preference.
   *
   * @param prefs Shared preferences from which to obtain the value.
   * @param res Resources from which to obtain the key and default value.
   * @param keyResId Resource identifier for the key.
   * @param value The value to store.
   */
  public static void putBooleanPref(
      SharedPreferences prefs, Resources res, int keyResId, boolean value) {
    final SharedPreferences.Editor editor = prefs.edit();
    editor.putBoolean(res.getString(keyResId), value);
    editor.commit();
  }

  /**
   * Stores the value of a {@link String} preference.
   *
   * @param prefs Shared preferences from which to obtain the value.
   * @param res Resources from which to obtain the key and default value.
   * @param keyResId Resource identifier for the key.
   * @param value The value to store.
   */
  public static void putStringPref(
      SharedPreferences prefs, Resources res, int keyResId, String value) {
    final SharedPreferences.Editor editor = prefs.edit();
    editor.putString(res.getString(keyResId), value);
    editor.commit();
  }

  /**
   * Stores the value of an integer preference.
   *
   * @param prefs Shared preferences from which to obtain the value.
   * @param res Resources from which to obtain the key and default value.
   * @param keyResId Resource identifier for the key.
   * @param value The value to store.
   */
  public static void putIntPref(SharedPreferences prefs, Resources res, int keyResId, int value) {
    final SharedPreferences.Editor editor = prefs.edit();
    editor.putInt(res.getString(keyResId), value);
    editor.apply();
  }

  /**
   * Stores the value of a boolean preference async. On Froyo and below it uses a background thread
   * for this. That means commits can happen out of order
   *
   * @param prefs Shared preferences from which to obtain the value.
   * @param res Resources from which to obtain the key and default value.
   * @param keyResId Resource identifier for the key.
   * @param value The value to store.
   */
  public static void storeBooleanAsync(
      final SharedPreferences prefs, final String key, final boolean value) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
      final SharedPreferences.Editor editor = prefs.edit();
      editor.putBoolean(key, value);
      editor.apply();
    } else {
      new Thread(
              new Runnable() {
                @Override
                public void run() {
                  final SharedPreferences.Editor editor = prefs.edit();
                  editor.putBoolean(key, value);
                  editor.commit();
                }
              })
          .start();
    }
  }
}
