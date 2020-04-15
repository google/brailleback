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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.a11y.brltty.core.*;

public class CoreThread extends Thread {
  private static final String LOG_TAG = CoreThread.class.getName();

  private final Context coreContext;

  public static final int DATA_MODE = Context.MODE_PRIVATE;
  public static final String DATA_TYPE_TABLES = "tables";
  public static final String DATA_TYPE_DRIVERS = "drivers";
  public static final String DATA_TYPE_STATE = "state";

  private final File getDataDirectory(String type) {
    return coreContext.getDir(type, DATA_MODE);
  }

  private final void emptyDirectory(File directory) {
    if (!directory.canWrite()) {
      directory.setWritable(true, true);
    }

    for (File file : directory.listFiles()) {
      if (file.isDirectory()) {
        emptyDirectory(file);
      }

      file.delete();
    }
  }

  private final void extractAsset(AssetManager assets, String asset, File path) {
    try {
      InputStream input = null;
      OutputStream output = null;

      try {
        input = assets.open(asset);
        output = new FileOutputStream(path);

        byte[] buffer = new byte[0X4000];
        int count;

        while ((count = input.read(buffer)) > 0) {
          output.write(buffer, 0, count);
        }
      } finally {
        if (input != null) {
          input.close();
        }

        if (output != null) {
          output.close();
        }
      }
    } catch (IOException exception) {
      Log.e(LOG_TAG, "cannot extract asset: " + asset + " -> " + path, exception);
    }
  }

  private final void extractAssets(
      AssetManager assets, String asset, File path, boolean executable) {
    try {
      String[] names = assets.list(asset);

      if (names.length == 0) {
        // Log.d(LOG_TAG, "extracting asset: " + asset + " -> " + path);
        extractAsset(assets, asset, path);

        if (executable) {
          path.setExecutable(true, false);
        }
      } else {
        if (!path.exists()) {
          // Log.d(LOG_TAG, "creating directory: " + path);
          path.mkdir();
        } else if (!path.isDirectory()) {
          Log.d(LOG_TAG, "not a directory: " + path);
          return;
        }

        for (String name : names) {
          extractAssets(assets, new File(asset, name).getPath(), new File(path, name), executable);
        }
      }

      path.setReadOnly();
    } catch (IOException exception) {
      Log.e(LOG_TAG, "cannot list asset: " + asset, exception);
    }
  }

  private final void extractAssets(AssetManager assets, String type, boolean executable) {
    File directory = getDataDirectory(type);
    emptyDirectory(directory);
    extractAssets(assets, type, directory, executable);
  }

  private final void extractAssets() {
    Log.d(LOG_TAG, "extracting assets");
    AssetManager assets = coreContext.getAssets();
    extractAssets(assets, DATA_TYPE_TABLES, false);
    extractAssets(assets, DATA_TYPE_DRIVERS, true);
    Log.d(LOG_TAG, "assets extracted");
  }

  CoreThread() {
    super("Core");
    coreContext = ApplicationHooks.getContext();
  }

  private String getStringResource(int resource) {
    return coreContext.getResources().getString(resource);
  }

  private boolean getBooleanSetting(int key, boolean defaultValue) {
    return ApplicationUtilities.getSharedPreferences()
        .getBoolean(getStringResource(key), defaultValue);
  }

  private boolean getBooleanSetting(int key) {
    return getBooleanSetting(key, false);
  }

  private String getStringSetting(int key, String defaultValue) {
    return ApplicationUtilities.getSharedPreferences()
        .getString(getStringResource(key), defaultValue);
  }

  private String getStringSetting(int key, int defaultValue) {
    return getStringSetting(key, getStringResource(defaultValue));
  }

  private String getStringSetting(int key) {
    return getStringSetting(key, "");
  }

  private Set<String> getStringSetSetting(int key) {
    return ApplicationUtilities.getSharedPreferences()
        .getStringSet(getStringResource(key), Collections.EMPTY_SET);
  }

  private String[] makeArguments() {
    ArgumentsBuilder builder = new ArgumentsBuilder();

    builder.setForegroundExecution(true);
    builder.setReleaseDevice(true);

    builder.setTablesDirectory(getDataDirectory(DATA_TYPE_TABLES).getPath());
    builder.setDriversDirectory(getDataDirectory(DATA_TYPE_DRIVERS).getPath());
    builder.setWritableDirectory(coreContext.getFilesDir().getPath());

    File stateDirectory = getDataDirectory(DATA_TYPE_STATE);
    builder.setUpdatableDirectory(stateDirectory.getPath());
    builder.setConfigurationFile(new File(stateDirectory, "default.conf").getPath());
    builder.setPreferencesFile(new File(stateDirectory, "default.prefs").getPath());

    builder.setTextTable(
        getStringSetting(R.string.PREF_KEY_TEXT_TABLE, R.string.DEFAULT_TEXT_TABLE));
    builder.setAttributesTable(
        getStringSetting(R.string.PREF_KEY_ATTRIBUTES_TABLE, R.string.DEFAULT_ATTRIBUTES_TABLE));
    builder.setContractionTable(
        getStringSetting(R.string.PREF_KEY_CONTRACTION_TABLE, R.string.DEFAULT_CONTRACTION_TABLE));
    builder.setKeyboardTable(
        getStringSetting(R.string.PREF_KEY_KEYBOARD_TABLE, R.string.DEFAULT_KEYBOARD_TABLE));

    {
      String name = getStringSetting(R.string.PREF_KEY_SELECTED_DEVICE);

      if (name.length() > 0) {
        Map<String, String> properties =
            SettingsActivity.getProperties(
                name,
                SettingsActivity.devicePropertyKeys,
                ApplicationUtilities.getSharedPreferences());

        String qualifier = properties.get(SettingsActivity.PREF_KEY_DEVICE_QUALIFIER);
        if (qualifier.length() > 0) {
          String reference = properties.get(SettingsActivity.PREF_KEY_DEVICE_REFERENCE);
          if (reference.length() > 0) {
            String driver = properties.get(SettingsActivity.PREF_KEY_DEVICE_DRIVER);
            if (driver.length() > 0) {
              builder.setBrailleDevice(qualifier + ":" + reference);
              builder.setBrailleDriver(driver);
            }
          }
        }
      }
    }

    builder.setSpeechDriver(
        getStringSetting(R.string.PREF_KEY_SPEECH_SUPPORT, R.string.DEFAULT_SPEECH_SUPPORT));
    builder.setQuietIfNoBraille(true);

    builder.setApiEnabled(true);
    builder.setApiParameters("host=127.0.0.1:0,auth=none");

    {
      ArrayList<String> keywords = new ArrayList<String>();
      keywords.add(getStringSetting(R.string.PREF_KEY_LOG_LEVEL, R.string.DEFAULT_LOG_LEVEL));
      keywords.addAll(getStringSetSetting(R.string.PREF_KEY_LOG_CATEGORIES));
      StringBuilder operand = new StringBuilder();

      for (String keyword : keywords) {
        if (keyword.length() > 0) {
          if (operand.length() > 0) operand.append(',');
          operand.append(keyword);
        }
      }

      builder.setLogLevel(operand.toString());
    }

    return builder.getArguments();
  }

  @Override
  public void run() {
    {
      SharedPreferences prefs = ApplicationUtilities.getSharedPreferences();
      File file = new File(coreContext.getPackageCodePath());

      String prefKey_size = getStringResource(R.string.PREF_KEY_PACKAGE_SIZE);
      long oldSize = prefs.getLong(prefKey_size, -1);
      long newSize = file.length();

      String prefKey_time = getStringResource(R.string.PREF_KEY_PACKAGE_TIME);
      long oldTime = prefs.getLong(prefKey_time, -1);
      long newTime = file.lastModified();

      if ((newSize != oldSize) || (newTime != oldTime)) {
        Log.d(LOG_TAG, "package size: " + oldSize + " -> " + newSize);
        Log.d(LOG_TAG, "package time: " + oldTime + " -> " + newTime);
        extractAssets();

        {
          SharedPreferences.Editor editor = prefs.edit();
          editor.putLong(prefKey_size, newSize);
          editor.putLong(prefKey_time, newTime);
          editor.commit();
        }
      }
    }

    BrailleRenderer.setBrailleRenderer(
        getStringSetting(R.string.PREF_KEY_NAVIGATION_MODE, R.string.DEFAULT_NAVIGATION_MODE));

    ApplicationParameters.LOG_ACCESSIBILITY_EVENTS =
        getBooleanSetting(R.string.PREF_KEY_LOG_ACCESSIBILITY_EVENTS);
    ApplicationParameters.LOG_RENDERED_SCREEN =
        getBooleanSetting(R.string.PREF_KEY_LOG_RENDERED_SCREEN);
    ApplicationParameters.LOG_KEYBOARD_EVENTS =
        getBooleanSetting(R.string.PREF_KEY_LOG_KEYBOARD_EVENTS);

    UsbHelper.begin();
    CoreWrapper.run(makeArguments(), ApplicationParameters.CORE_WAIT_DURATION);
    UsbHelper.end();
  }
}
