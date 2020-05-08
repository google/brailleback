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

package com.googlecode.eyesfree.brailleback;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import com.googlecode.eyesfree.braille.display.BrailleDisplayProperties;
import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.braille.display.BrailleKeyBinding;
import com.googlecode.eyesfree.braille.display.Display;
import com.googlecode.eyesfree.braille.display.DisplayClient;
import com.googlecode.eyesfree.brailleback.utils.BrailleKeyBindingUtils;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoRef;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import java.util.ArrayList;
import java.util.Map;

/**
 * Activity explaining how to use incremental search mode.
 */
public class SearchTutorialActivity extends Activity
        implements Display.OnConnectionStateChangeListener {
    private static final String PREF_DO_NOT_SHOW = "search_tutorial_no_show";

    private DisplayClient mDisplay;

    // Whether the activity was closed through the close button.
    private boolean mShouldActivateSearch = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.search_tutorial_activity);

        final SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(this);
        CheckBox doNotShowCheckBox =
                (CheckBox) findViewById(R.id.doNotShowCheckBox);
        doNotShowCheckBox.setChecked(prefs.getBoolean(PREF_DO_NOT_SHOW, false));
    }

    @Override
    protected void onStart() {
        mDisplay = new DisplayClient(this);
        mDisplay.setOnConnectionStateChangeListener(this);
        super.onStart();
    }

    @Override
    protected void onStop() {
        if (mDisplay != null) {
            mDisplay.shutdown();
            mDisplay = null;
        }
        if (mShouldActivateSearch) {
            if (BrailleBackService.getActiveInstance() != null) {
                BrailleBackService.getActiveInstance().startSearchMode();
            }
            mShouldActivateSearch = false;
        }
        super.onStop();
    }

    @Override
    public void onConnectionStateChanged(int state) {
        if (state == Display.STATE_CONNECTED) {
            populateTutorialText();
        } else {
            // Hide specific instructions if no display connected.
            TextView instructionsTextView =
                    (TextView) findViewById(R.id.instructionsSpecific);
            instructionsTextView.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Fills in the tutorial text view. Adds in key bindings as arguments
     * if possible.
     */
    private void populateTutorialText() {
        // Find friendly names for key commands.
        BrailleDisplayProperties props = mDisplay.getDisplayProperties();
        if (props == null) {
            return;
        }

        ArrayList<BrailleKeyBinding> sortedBindings =
                BrailleKeyBindingUtils.getSortedBindingsForDisplay(props);
        Map<String, String> friendlyKeyNames = props.getFriendlyKeyNames();

        // Find bindings for the four commands we need.
        BrailleKeyBinding nextItemBinding =
                BrailleKeyBindingUtils.getBrailleKeyBindingForCommand(
                    BrailleInputEvent.CMD_NAV_ITEM_NEXT,
                    sortedBindings);
        BrailleKeyBinding previousItemBinding =
                BrailleKeyBindingUtils.getBrailleKeyBindingForCommand(
                    BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS,
                    sortedBindings);
        BrailleKeyBinding enterBinding =
                BrailleKeyBindingUtils.getBrailleKeyBindingForCommand(
                    BrailleInputEvent.CMD_KEY_ENTER,
                    sortedBindings);
        BrailleKeyBinding toggleSearchBinding =
                BrailleKeyBindingUtils.getBrailleKeyBindingForCommand(
                    BrailleInputEvent.CMD_TOGGLE_INCREMENTAL_SEARCH,
                    sortedBindings);

        if (nextItemBinding == null || previousItemBinding == null ||
            enterBinding == null || toggleSearchBinding == null) {
            // Stop here if any of the bindings aren't set.
            return;
        }

        String nextItemName =
                BrailleKeyBindingUtils.getFriendlyKeyNamesForCommand(
                    nextItemBinding, friendlyKeyNames, this);
        String previousItemName =
                BrailleKeyBindingUtils.getFriendlyKeyNamesForCommand(
                    previousItemBinding, friendlyKeyNames, this);
        String enterName =
                BrailleKeyBindingUtils.getFriendlyKeyNamesForCommand(
                    enterBinding, friendlyKeyNames, this);
        String toggleSearchName =
                BrailleKeyBindingUtils.getFriendlyKeyNamesForCommand(
                    toggleSearchBinding, friendlyKeyNames, this);

        // Set text in text view.
        TextView instructionsTextView =
                (TextView) findViewById(R.id.instructionsSpecific);

        instructionsTextView.setText(getString(
                R.string.search_tutorial_instructions_specific,
                nextItemName,
                previousItemName,
                enterName,
                toggleSearchName));
        instructionsTextView.setVisibility(View.VISIBLE);
    }

    /**
     * Tries to start the activity. May do nothing if stored preferences
     * say to not show the tutorial again. Returns whether the tutorial
     * was actually started.
     */
    /*package*/ static boolean tryStartActivity(final Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(PREF_DO_NOT_SHOW, false)) {
            return false;
        }

        Intent intent = new Intent(context, SearchTutorialActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
        return true;
    }

    /**
     * Closes the activity and saves a preference about whether or not
     * to display the tutorial in the future.
     */
    public void onClickCloseButton(View v) {
        final SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(this);
        CheckBox doNotShowCheckBox =
                (CheckBox) findViewById(R.id.doNotShowCheckBox);
        prefs.edit()
             .putBoolean(PREF_DO_NOT_SHOW, doNotShowCheckBox.isChecked())
             .apply();
        mShouldActivateSearch = true;
        finish();
    }
}
