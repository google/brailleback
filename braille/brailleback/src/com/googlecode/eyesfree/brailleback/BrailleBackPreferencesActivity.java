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

import android.content.Intent;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.text.TextUtils;
import android.util.Log;

import com.googlecode.eyesfree.braille.display.Display;
import com.googlecode.eyesfree.braille.display.DisplayClient;
import com.googlecode.eyesfree.braille.translate.TableInfo;
import com.googlecode.eyesfree.brailleback.utils.PreferenceUtils;
import com.googlecode.eyesfree.utils.LogUtils;

import java.text.CollationKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Activity used to set BrailleBack's service preferences.
 */
public class BrailleBackPreferencesActivity extends PreferenceActivity
    implements Display.OnConnectionStateChangeListener,
               Display.OnConnectionChangeProgressListener,
               TranslatorManager.OnTablesChangedListener,
               Preference.OnPreferenceChangeListener,
               Preference.OnPreferenceClickListener {
    private final TableInfoComparator TABLE_INFO_COMPARATOR =
            new TableInfoComparator();

    private DisplayClient mDisplay;
    private TranslatorManager mTranslatorManager;
    private Preference mStatusPreference;
    private ListPreference mBrailleTypePreference;
    private ListPreference mSixDotTablePreference;
    private ListPreference mEightDotTablePreference;
    private Preference mOverlayPreference;
    private Preference mOverlayTutorialPreference;
    private Preference mLicensesPreference;
    private ListPreference mLogLevelPreference;
    private int mConnectionState = Display.STATE_NOT_CONNECTED;
    private String mConnectionProgress = null;
    private List<TableInfo> mTables;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        mStatusPreference =
                findPreferenceByResId(R.string.pref_connection_status_key);
        mStatusPreference.setOnPreferenceClickListener(this);
        assignKeyBindingsIntent();

        mBrailleTypePreference = (ListPreference) findPreferenceByResId(
                R.string.pref_braille_type_key);
        mBrailleTypePreference.setOnPreferenceChangeListener(this);
        mSixDotTablePreference = (ListPreference) findPreferenceByResId(
                R.string.pref_six_dot_braille_table_key);
        mSixDotTablePreference.setOnPreferenceChangeListener(this);
        mEightDotTablePreference = (ListPreference) findPreferenceByResId(
                R.string.pref_eight_dot_braille_table_key);
        mEightDotTablePreference.setOnPreferenceChangeListener(this);

        mOverlayPreference = findPreferenceByResId(
                R.string.pref_braille_overlay_key);
        mOverlayPreference.setOnPreferenceChangeListener(this);

        mOverlayTutorialPreference = findPreferenceByResId(
                R.string.pref_braille_overlay_tutorial_key);
        mOverlayTutorialPreference.setOnPreferenceClickListener(this);

        mLicensesPreference = findPreferenceByResId(R.string.pref_os_license_key);
        mLicensesPreference.setOnPreferenceClickListener(this);
        mLogLevelPreference = (ListPreference) findPreferenceByResId(
                R.string.pref_log_level_key);
        mLogLevelPreference.setOnPreferenceChangeListener(this);
        if (BuildConfig.DEBUG) {
            int logLevel = PreferenceUtils.getLogLevel(this);
            updateListPreferenceSummary(mLogLevelPreference,
                    Integer.toString(logLevel));
            mLogLevelPreference.setEnabled(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mDisplay = new DisplayClient(this);
        mDisplay.setOnConnectionStateChangeListener(this);
        mDisplay.setOnConnectionChangeProgressListener(this);
        mTranslatorManager = new TranslatorManager(this);
        mTranslatorManager.addOnTablesChangedListener(this);
        onConnectionStateChanged(Display.STATE_NOT_CONNECTED);
    }

    @Override
    public void onPause() {
        super.onPause();
        mTranslatorManager.removeOnTablesChangedListener(this);
        mTranslatorManager.shutdown();
        mDisplay.shutdown();
    }

    @Override
    public void onConnectionStateChanged(int state) {
        mConnectionState = state;
        CharSequence summary;
        boolean enableBindings = false;
        switch (state) {
            case Display.STATE_CONNECTED:
                summary = getText(R.string.connstate_connected);
                enableBindings = true;
                break;
            case Display.STATE_NOT_CONNECTED:
                summary = getText(R.string.connstate_not_connected);
                break;
            default:
                summary = getText(R.string.connstate_error);
                break;
        }
        Preference bindingsPref =
                findPreferenceByResId(R.string.pref_key_bindings_key);
        bindingsPref.setEnabled(enableBindings);
        if (mConnectionProgress == null) {
            mStatusPreference.setSummary(summary);
            announceConnectionState(summary);
        }
    }

    @Override
    public void onConnectionChangeProgress(String description) {
        mConnectionProgress = description;
        if (description == null) {
            onConnectionStateChanged(mConnectionState);
            return;
        }
        // The description is localized by the server.
        mStatusPreference.setSummary(description);
        announceConnectionState(description);
    }

    private void announceConnectionState(CharSequence state) {
        // TODO: Ideally, this announcement would be sent from the
        // view of the actual preference, if there only was a way to get
        // to that node.
        getWindow().getDecorView().announceForAccessibility(state);
    }

    @Override
    public void onTablesChanged() {
        mTables = mTranslatorManager.getTranslatorClient().getTables();
        addTableList(mSixDotTablePreference, false);
        addTableList(mEightDotTablePreference, true);
    }

    /**
     * Assigns the appropriate intent to the key bindings preference.
     */
    private void assignKeyBindingsIntent() {
        Preference pref =
                findPreferenceByResId(R.string.pref_key_bindings_key);

        final Intent intent = new Intent(this, KeyBindingsActivity.class);

        pref.setIntent(intent);
    }

    /**
     * Returns the preference associated with the specified resource identifier.
     *
     * @param resId A string resource identifier.
     * @return The preference associated with the specified resource identifier.
     */
    @SuppressWarnings("deprecation")
    private Preference findPreferenceByResId(int resId) {
        return findPreference(getString(resId));
    }

    private void addTableList(ListPreference pref, boolean eightDot) {
        ArrayList<TableInfo> tables = new ArrayList<TableInfo>();
        for (TableInfo info : mTables) {
            if (eightDot == info.isEightDot()) {
                tables.add(info);
            }
        }
        Collections.sort(tables, TABLE_INFO_COMPARATOR);
        CharSequence[] entryValues = new CharSequence[tables.size() + 1];
        CharSequence[] entries = new CharSequence[tables.size() + 1];
        int index = 0;
        TableInfo defaultInfo = mTranslatorManager.findDefaultTableInfo(
                eightDot);
        if (defaultInfo != null) {
            entries[index] = getString(
                    R.string.pref_braille_table_default_label,
                    createTableDisplayName(defaultInfo));
        } else {
            entries[index] = getText(
                    R.string.pref_braille_table_default_none_label);
        }
        entryValues[index] = getString(R.string.table_value_default);
        ++index;
        for (TableInfo info : tables) {
            entries[index] = createTableDisplayName(info);
            entryValues[index] = info.getId();
            ++index;
        }
        pref.setEntries(entries);
        pref.setEntryValues(entryValues);

        index = pref.findIndexOfValue(pref.getValue());
        if (index < 0 || index >= entries.length) {
            LogUtils.log(this, Log.ERROR,
                    "Unknown preference value for %s: %s",
                    pref.getKey(), pref.getValue());
        } else {
            pref.setSummary(entries[index]);
        }
    }

    private String createTableDisplayName(TableInfo tableInfo) {
        String localeDisplayName = tableInfo.getLocale().getDisplayName();
        if (tableInfo.isEightDot()) {
            // The fact that this is computer braille is obvious
            // from context.
            return localeDisplayName;
        }
        List<TableInfo> related =
                mTranslatorManager.getRelatedTables(tableInfo);
        int gradeCount = 0;
        for (TableInfo relatedInfo : related) {
            if (relatedInfo.isEightDot()) {
                continue;
            }
            ++gradeCount;
        }
        if (gradeCount <= 1) {
            // Only one of our kind.
            if (TextUtils.isEmpty(tableInfo.getVariant())) {
                return localeDisplayName;
            } else {
                return getString(R.string.table_name_variant,
                        localeDisplayName, tableInfo.getVariant());
            }
        }
        if (TextUtils.isEmpty(tableInfo.getVariant())) {
            return getString(R.string.table_name_grade,
                    localeDisplayName, tableInfo.getGrade());

        } else {
            return getString(R.string.table_name_variant_grade,
                    localeDisplayName, tableInfo.getVariant(), tableInfo.getGrade());
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        // Always update the summary based on the list preference value.
        if (preference instanceof ListPreference) {
            boolean updated = updateListPreferenceSummary(
                (ListPreference) preference, (String) newValue);
            if (!updated) {
                return false;
            }
        }

        // If the overlay was turned on for the first time, launch the tutorial.
        if (preference == mOverlayPreference && newValue.equals(true)) {
            OverlayTutorialActivity.startIfFirstTime(this);
        }

        // If the log level was changed, update it in LogUtils.
        if (preference == mLogLevelPreference) {
            try {
                int logLevel = Integer.parseInt((String) newValue);
                return PreferenceUtils.updateLogLevel(logLevel);
            } catch (IllegalArgumentException e) {
                LogUtils.log(this, Log.ERROR,
                        "illegal log level: %s", newValue);
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference pref) {
        if (pref == mStatusPreference) {
            mDisplay.poll();
            return true;
        } else if (pref == mOverlayTutorialPreference) {
            startActivity(new Intent(this, OverlayTutorialActivity.class));
            return true;
        }  else if (pref == mLicensesPreference) {
            Intent intent = WebViewDialog.getIntent(this,
                    R.string.pref_os_license_title,
                    "file:///android_asset/licenses.html");
            startActivity(intent);
        }
        return false;
    }

    private boolean updateListPreferenceSummary(
            ListPreference listPreference, String newValue) {
        int index = listPreference.findIndexOfValue(newValue);
        CharSequence entries[] = listPreference.getEntries();
        if (index < 0 || index >= entries.length) {
            LogUtils.log(this, Log.ERROR,
                    "Unknown preference value for %s: %s",
                    listPreference.getKey(), newValue);
            return false;
        }
        listPreference.setSummary(entries[index]);
        return true;
    }

    private static class TableInfoComparator
            implements Comparator<TableInfo> {
        private static final int KEY_MAP_INITIAL_CAPACITY = 50;
        private final Collator mCollator = Collator.getInstance();
        private final Map<TableInfo, CollationKey> mCollationKeyMap =
            new HashMap<TableInfo, CollationKey>(KEY_MAP_INITIAL_CAPACITY);

        @Override
        public int compare(TableInfo first, TableInfo second) {
            if (first.equals(second)) {
                return 0;
            }
            int ret = getCollationKey(first).compareTo(getCollationKey(second));
            if (ret == 0 && first.isEightDot() != second.isEightDot()) {
                ret = first.isEightDot() ? 1 : -1;
            }
            if (ret == 0) {
                ret = first.getGrade() - second.getGrade();
            }
            return ret;
        }

        private CollationKey getCollationKey(TableInfo tableInfo) {
            CollationKey key = mCollationKeyMap.get(tableInfo);
            if (key == null) {
                key = mCollator.getCollationKey(
                    tableInfo.getLocale().getDisplayName());
                mCollationKeyMap.put(tableInfo, key);
            }
            return key;
        }
    }
}
