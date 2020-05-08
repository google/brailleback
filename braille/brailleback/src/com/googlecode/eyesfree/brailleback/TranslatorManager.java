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

package com.googlecode.eyesfree.brailleback;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.preference.PreferenceManager;
import android.util.Log;
import com.googlecode.eyesfree.braille.translate.BrailleTranslator;
import com.googlecode.eyesfree.braille.translate.TableInfo;
import com.googlecode.eyesfree.braille.translate.TranslatorClient;
import com.googlecode.eyesfree.utils.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keeps track of the current braille translator as determined by
 * the current user locale and settings.
 *
 * Threading: This class is not thread safe,except {@link getTranslator} and
 * {@link #getUncontractedTranslator} may be called from any thread.  Note
 * that the result from calling those functions may not be consistent while
 * the selected tables change.  This shouldn't be a problem if a caller uses
 * the callback API to retranslate the display content on configuration
 * changes.
 */
public class TranslatorManager
    implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final String mPrefBrailleTypeKey;
    private final String mPrefBrailleTypeSixDotValue;
    private final String mPrefBrailleTypeEightDotValue;
    private final String mPrefBrailleTypeDefault;
    private final String mPrefSixDotBrailleTableKey;
    private final String mPrefEightDotBrailleTableKey;
    private final String mTableValueDefault;

    private final SharedPreferences mSharedPreferences;
    private final TranslatorClient mTranslatorClient;
    private volatile BrailleTranslator mTranslator;
    private volatile BrailleTranslator mUncontractedTranslator;
    private final List<OnTablesChangedListener> mOnTablesChangedListeners =
            new ArrayList<OnTablesChangedListener>();
    private List<TableInfo> mTables;
    private final Map<Locale, List<TableInfo>> mLocalesToTables
            = new HashMap<Locale, List<TableInfo>>();
    private Locale mLocale;
    private boolean mClientInitialized = false;

    /**
     * Callback interface to be invoked when one or both of the current
     * translation tables have changed.
     */
    public interface OnTablesChangedListener {
        /**
         * Called when one or both translation tables have changed, including
         * when the translation service is initialized so that tables are
         * available.
         */
        void onTablesChanged();
    }

    /**
     * Constructs an instance, creating a connection to the translation
     * service and setting up this instance to provide tables for the current
     * user configuration.
     */
    public TranslatorManager(final Context context) {
        mPrefBrailleTypeKey = context.getString(
                R.string.pref_braille_type_key);
        mPrefBrailleTypeSixDotValue = context.getString(
                R.string.pref_braille_type_six_dot_value);
        mPrefBrailleTypeEightDotValue = context.getString(
                R.string.pref_braille_type_eight_dot_value);
        mPrefBrailleTypeDefault = context.getString(
                R.string.pref_braille_type_default);
        mPrefSixDotBrailleTableKey = context.getString(
                R.string.pref_six_dot_braille_table_key);
        mPrefEightDotBrailleTableKey = context.getString(
                R.string.pref_eight_dot_braille_table_key);
        mTableValueDefault = context.getString(
                R.string.table_value_default);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(
                context);
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
        mTranslatorClient = new TranslatorClient(
                context, new TranslatorClient.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        if (status != TranslatorClient.SUCCESS) {
                            LogUtils.log(TranslatorManager.this, Log.ERROR,
                                    "Couldn't initialize braille translator");
                            return;
                        }
                        mClientInitialized = true;
                        onConfigurationChanged(
                                context.getResources().getConfiguration());
                    }
                });
    }

    /**
     * Frees resources used by this instance, most notably the connection to
     * the translation service.
     */
    public void shutdown() {
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        mTranslatorClient.destroy();
        mClientInitialized = false;
        mTranslator = null;
        mUncontractedTranslator = null;
    }

    /**
     * Adds a listener to be called when the translation tables have changed.
     */
    public void addOnTablesChangedListener(OnTablesChangedListener listener) {
        mOnTablesChangedListeners.add(listener);
    }

  /** Removes a table change listener. */
  public void removeOnTablesChangedListener(OnTablesChangedListener listener) {
        mOnTablesChangedListeners.remove(listener);
    }

    /**
     * Returns the main braille translor to use.
     */
    public BrailleTranslator getTranslator() {
        return mTranslator;
    }

    /**
     * Returns a translator to be used when contractions shouldn't be used.
     * This is for use when editing text.
     */
    public BrailleTranslator getUncontractedTranslator() {
        return mUncontractedTranslator;
    }

    /**
     * Returns the translator client used by this instance.
     */
    public TranslatorClient getTranslatorClient() {
        return mTranslatorClient;
    }

    /**
     * Returns all braille tables for the same locale as {@code tableInfo}.
     */
    public List<TableInfo> getRelatedTables(TableInfo tableInfo) {
        return Collections.unmodifiableList(
                mLocalesToTables.get(tableInfo.getLocale()));
    }

    /**
     * Reevaluates what should be the current translation table based on the
     * new configuration.
     */
    public void onConfigurationChanged(Configuration newConfiguration) {
        if (!mClientInitialized || newConfiguration.locale.equals(mLocale)) {
            return;
        }
        mLocale = newConfiguration.locale;
        mTables = mTranslatorClient.getTables();
        updateLocalesToTables();
        updateTranslators();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs,
            String key) {
        if (mClientInitialized && isTablePreferenceKey(key)) {
            updateTranslators();
        }
    }

    private void updateLocalesToTables() {
        mLocalesToTables.clear();
        for (TableInfo info : mTables) {
            Locale locale = info.getLocale();
            if (!mLocalesToTables.containsKey(locale)) {
                mLocalesToTables.put(locale, new ArrayList<TableInfo>());
            }
            mLocalesToTables.get(locale).add(info);
        }
    }

    private void updateTranslators() {
        boolean eightDot = isEightDotBrailleSelected();
        BrailleTranslator newTranslator =
                findBrailleTranslator(eightDot, true/*fallback*/);
        if (newTranslator == null) {
            LogUtils.log(this, Log.ERROR, "Couldn't find braille translator "
                    + "for %s", mLocale);
            return;
        }
        // TODO: For six dot braille, get the table with smallest grade.
        // This requires IME changes.
        BrailleTranslator newUncontractedTranslator =
                findBrailleTranslator(true/*eightDot*/, true/*fallback*/);
        if (newUncontractedTranslator == null) {
            newUncontractedTranslator = newTranslator;
        }
        boolean changed = !newTranslator.equals(mTranslator)
                || !newUncontractedTranslator.equals(mUncontractedTranslator);
        mTranslator = newTranslator;
        mUncontractedTranslator = newUncontractedTranslator;
        if (changed) {
            callOnTablesChangedListeners();
        }
    }

    public TableInfo findDefaultTableInfo(
            boolean eightDot) {
        if (mTables == null) {
            LogUtils.log(this, Log.ERROR, "Couldn't get translation tables");
            return null;
        }
        TableInfo best = null;
        for (TableInfo info : mTables) {
            if (eightDot != info.isEightDot()) {
                continue;
            }
            if (betterTable(info, best)) {
                best = info;
            }
        }
        return best;
    }

    private BrailleTranslator getTranslatorFromPreferences(boolean eightDot) {
        String key = eightDot
                ? mPrefEightDotBrailleTableKey
                : mPrefSixDotBrailleTableKey;
        String value = mSharedPreferences.getString(key,
                mTableValueDefault);
        if (mTableValueDefault.equals(value)) {
            return null;
        }
        return mTranslatorClient.getTranslator(value);
    }

    /**
     * Finds a translation table for the {@code userLocale},
     * falling back on US English if no match is found.
     */
    private BrailleTranslator findBrailleTranslator(boolean eightDot,
            boolean fallback) {
        BrailleTranslator translator = getTranslatorFromPreferences(eightDot);
        if (translator != null) {
            return translator;
        }
        TableInfo info = findDefaultTableInfo(eightDot);
        if (info == null && fallback) {
            info = findFallbackTableInfo();
        }
        if (info != null) {
            translator = mTranslatorClient.getTranslator(info.getId());
        }
        return translator;
    }

    private TableInfo findFallbackTableInfo() {
        for (TableInfo table : mTables) {
            if (table.getLocale().equals(Locale.US)) {
                return table;
            }
        }
        return null;
    }

    private boolean betterTable(TableInfo first, TableInfo second) {
        Locale firstLocale = first.getLocale();
        Locale secondLocale = second != null
                ? second.getLocale()
                : Locale.ROOT;
        return matchRank(firstLocale, mLocale)
                > matchRank(secondLocale, mLocale);
    }

    private static int matchRank(Locale first, Locale second) {
        int ret = first.getLanguage().equals(second.getLanguage()) ? 1 : 0;
        if (ret > 0) {
            ret += (first.getCountry().equals(second.getCountry()) ? 1 : 0);
            if (ret > 1) {
                ret += (first.getVariant().equals(second.getVariant()) ? 1 : 0);
            }
        }
        return ret;
    }

    private void callOnTablesChangedListeners() {
        for (OnTablesChangedListener listener : mOnTablesChangedListeners) {
            listener.onTablesChanged();
        }
    }

    private boolean isTablePreferenceKey(String key) {
        return mPrefBrailleTypeKey.equals(key)
                || mPrefSixDotBrailleTableKey.equals(key)
                || mPrefEightDotBrailleTableKey.equals(key);
    }

    /**
     * Returns whether eight dot braille is selected in the
     * preferences.
     */
    private boolean isEightDotBrailleSelected() {
        String value = mSharedPreferences.getString(mPrefBrailleTypeKey,
                mPrefBrailleTypeDefault);
        return mPrefBrailleTypeEightDotValue.equals(value);
    }
}
