/*
 * Copyright (C) 2013 Google Inc.
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

package com.googlecode.eyesfree.brailleback.utils;

import com.googlecode.eyesfree.brailleback.BuildConfig;
import com.googlecode.eyesfree.brailleback.R;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.SharedPreferencesUtils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * BrailleBack-specific preference utilities.
 */
public class PreferenceUtils {

    /** Log level forced in debug builds. */
    private static int DEBUG_LOG_LEVEL = Log.VERBOSE;

    /**
     * Initializes the {@link LogUtils} log level from the shared preferences.
     * In debug builds, this is locked to be "VERBOSE".
     * In release builds, it is user-configurable.
     */
    public static void initLogLevel(Context context) {
        int logLevel = getLogLevel(context);
        LogUtils.setLogLevel(logLevel);
    }

    /**
     * Updates the log level, if this preference can be edited in this build.
     * @return True if the log level was changed.
     */
    public static boolean updateLogLevel(int logLevel) {
        if (BuildConfig.DEBUG) {
            return false;
        }
        LogUtils.setLogLevel(logLevel);
        return true;
    }

    /**
     * Returns the appropriate log level to use, given the build type and user
     * preferences. In debug builds, this is forced to be "VERBOSE".
     */
    public static int getLogLevel(Context context) {
        if (BuildConfig.DEBUG) {
            return DEBUG_LOG_LEVEL;
        } else {
            SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
            return SharedPreferencesUtils.getIntFromStringPref(
                prefs, context.getResources(), R.string.pref_log_level_key,
                R.string.pref_log_level_default);
        }
    }

    private PreferenceUtils() {}
}
